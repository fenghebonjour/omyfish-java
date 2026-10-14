package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CheckoutOrchestrationService implements CheckoutUseCase, ConfirmPaymentWebhookUseCase {

    private final ReserveOrderUseCase reserveOrder;
    private final CancelOrderUseCase cancelOrder;
    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;

    public CheckoutOrchestrationService(
        ReserveOrderUseCase reserveOrder, CancelOrderUseCase cancelOrder,
        OrderRepository orderRepository, PaymentPort paymentPort
    ) {
        this.reserveOrder = reserveOrder;
        this.cancelOrder = cancelOrder;
        this.orderRepository = orderRepository;
        this.paymentPort = paymentPort;
    }

    @Override
    public CheckoutResult checkout(CartOwner owner, String guestEmail, String successUrl, String cancelUrl) {
        if (!paymentPort.isConfigured()) {
            throw new IllegalStateException("No payment processor is configured");
        }

        // Stock is decremented and the order is created in its own DB transaction (via the
        // reserveOrder wrapper) BEFORE the Stripe call below, so the Stripe HTTP round-trip
        // never holds a DB transaction open. If the Stripe call then fails, cancelOrder
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

        order.attachCheckoutSession(session.get().sessionId());
        orderRepository.save(order);

        return new CheckoutResult(order.getId(), session.get().checkoutUrl());
    }

    @Override
    public boolean handleWebhook(String payload, Map<String, String> headers) {
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

        // Status guards make webhook retries idempotent — Stripe may redeliver the same event.
        switch (event.type()) {
            case SESSION_COMPLETED -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.markPaid(event.stripePaymentIntentId(), event.shippingAddress());
                    orderRepository.save(order);
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
