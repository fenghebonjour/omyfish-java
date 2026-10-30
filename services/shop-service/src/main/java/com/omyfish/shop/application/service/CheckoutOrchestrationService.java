package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.model.IdempotencyRecord;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.IdempotencyConflictException;
import com.omyfish.shop.domain.port.out.IdempotencyKeyRepository;
import com.omyfish.shop.domain.port.out.OrderEventPublisher;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CheckoutOrchestrationService implements CheckoutUseCase, ConfirmPaymentWebhookUseCase {

    /**
     * How long an incomplete checkout reservation sits before it's treated as orphaned rather
     * than "still in progress" — same role as identity-service BillingService's
     * STALE_RESERVATION_AGE. Comfortably past the payment adapters' own worst-case call time
     * (StripeHttpSupport: 10s connect + 15s read), so a reservation only outlives it if the
     * process died before completing/deleting it.
     */
    private static final Duration STALE_RESERVATION_AGE = Duration.ofMinutes(1);

    private final ReserveOrderUseCase reserveOrder;
    private final CancelOrderUseCase cancelOrder;
    private final OrderRepository orderRepository;
    private final ShopPaymentProcessorRegistry processors;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final OrderEventPublisher orderEventPublisher;

    public CheckoutOrchestrationService(
        ReserveOrderUseCase reserveOrder, CancelOrderUseCase cancelOrder,
        OrderRepository orderRepository, ShopPaymentProcessorRegistry processors,
        IdempotencyKeyRepository idempotencyKeys, OrderEventPublisher orderEventPublisher
    ) {
        this.reserveOrder = reserveOrder;
        this.cancelOrder = cancelOrder;
        this.orderRepository = orderRepository;
        this.processors = processors;
        this.idempotencyKeys = idempotencyKeys;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    public CheckoutResult checkout(
        CartOwner owner, String guestEmail, String successUrl, String cancelUrl, String idempotencyKey
    ) {
        String ownerKey = ownerKey(owner);

        Optional<IdempotencyRecord> existing = idempotencyKeys.find(idempotencyKey, IdempotencyRecord.CHECKOUT);
        if (existing.isPresent() && !isOrphaned(existing.get())) {
            return replayedCheckout(existing.get(), ownerKey);
        }

        PaymentPort paymentPort = processors.defaultProcessor()
            .orElseThrow(() -> new IllegalStateException("No payment processor is configured"));

        IdempotencyRecord reservation = existing.isPresent()
            ? requireOwner(existing.get(), ownerKey)
            : idempotencyKeys.reserve(idempotencyKey, IdempotencyRecord.CHECKOUT, ownerKey);

        try {
            // Stock is decremented and the order is created in its own DB transaction (via the
            // reserveOrder wrapper) BEFORE the processor call below, so the HTTP round-trip never
            // holds a DB transaction open. If the processor call then fails, cancelOrder
            // compensates by restocking and cancelling the order it just created.
            Order order = reserveOrder.reserve(owner, guestEmail);

            List<PaymentPort.LineItem> lineItems = order.getLines().stream()
                .map(l -> new PaymentPort.LineItem(l.productName(), l.unitPriceCents(), l.quantity()))
                .toList();

            Optional<PaymentPort.CheckoutSession> session;
            try {
                session = paymentPort.createCheckoutSession(
                    order.getId(), lineItems, successUrl, cancelUrl, order.getId().toString(), guestEmail);
            } catch (RuntimeException e) {
                cancelOrder.cancelAndRestock(order.getId());
                throw new IllegalStateException("Checkout session creation failed: " + e.getMessage(), e);
            }

            if (session.isEmpty()) {
                cancelOrder.cancelAndRestock(order.getId());
                throw new IllegalStateException("No payment processor is configured");
            }

            order.attachCheckoutSession(session.get().sessionId(), paymentPort.name());
            orderRepository.save(order);

            reservation.completeCheckout(order.getId(), session.get().checkoutUrl());
            idempotencyKeys.save(reservation);

            return new CheckoutResult(order.getId(), session.get().checkoutUrl(), false);
        } catch (RuntimeException e) {
            idempotencyKeys.delete(reservation);
            throw e;
        }
    }

    private CheckoutResult replayedCheckout(IdempotencyRecord record, String ownerKey) {
        requireOwner(record, ownerKey);
        if (!record.isCompleted()) {
            throw new IdempotencyConflictException(
                "A checkout with this idempotency key is already in progress");
        }
        return new CheckoutResult(record.getOrderId(), record.getCheckoutUrl(), true);
    }

    private boolean isOrphaned(IdempotencyRecord record) {
        return !record.isCompleted() && record.isStale(STALE_RESERVATION_AGE);
    }

    private IdempotencyRecord requireOwner(IdempotencyRecord record, String ownerKey) {
        if (!record.getOwnerKey().equals(ownerKey)) {
            throw new IllegalArgumentException("Idempotency key already used");
        }
        return record;
    }

    private static String ownerKey(CartOwner owner) {
        return owner.isGuest() ? "guest:" + owner.guestToken() : "user:" + owner.userId();
    }

    @Override
    public boolean handleWebhook(String processor, String payload, Map<String, String> headers) {
        PaymentPort paymentPort;
        try {
            paymentPort = processors.byName(processor);
        } catch (IllegalStateException e) {
            return false;
        }

        Optional<PaymentPort.PaymentEvent> eventOpt = paymentPort.verifyWebhook(payload, headers);
        if (eventOpt.isEmpty()) {
            return false;
        }
        PaymentPort.PaymentEvent event = eventOpt.get();

        Optional<Order> orderOpt = orderRepository.findByStripeSessionId(event.stripeSessionId());
        if (orderOpt.isEmpty()) {
            return false;
        }
        Order order = orderOpt.get();

        // Status guards make webhook retries idempotent — a processor may redeliver the same event.
        switch (event.type()) {
            case SESSION_COMPLETED -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.markPaid(event.stripePaymentIntentId(), event.shippingAddress());
                    orderRepository.save(order);
                    // Published on payment confirmation, not at reservation time (M1) — the
                    // confirmation email and audit ledger this feeds should only fire for an
                    // order that was actually paid, not one still awaiting a payment that might
                    // fail or be abandoned on the processor's page.
                    orderEventPublisher.publishOrderPlaced(order);
                }
            }
            case SESSION_EXPIRED -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    cancelOrder.cancelAndRestock(order.getId());
                }
            }
        }
        return true;
    }
}
