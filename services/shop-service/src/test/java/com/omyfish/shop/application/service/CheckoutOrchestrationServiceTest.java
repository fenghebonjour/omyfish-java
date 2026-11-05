package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.model.IdempotencyRecord;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.IdempotencyConflictException;
import com.omyfish.shop.domain.port.out.IdempotencyKeyRepository;
import com.omyfish.shop.domain.port.out.OrderEventPublisher;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutOrchestrationServiceTest {

    @Mock ReserveOrderUseCase reserveOrder;
    @Mock CancelOrderUseCase cancelOrder;
    @Mock OrderRepository orderRepository;
    @Mock ShopPaymentProcessorRegistry processors;
    @Mock PaymentPort paymentPort;
    @Mock IdempotencyKeyRepository idempotencyKeys;
    @Mock OrderEventPublisher orderEventPublisher;
    @InjectMocks CheckoutOrchestrationService service;

    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    /** An incomplete reservation old enough for the service to treat as orphaned, not in-progress. */
    private static IdempotencyRecord staleRecord(String key, String ownerKey) {
        IdempotencyRecord record = IdempotencyRecord.reserve(key, IdempotencyRecord.CHECKOUT, ownerKey);
        ReflectionTestUtils.setField(record, "createdAt", Instant.now().minus(Duration.ofMinutes(5)));
        return record;
    }

    @Test
    void checkout_happyPath_attachesSessionAndReturnsCheckoutUrl() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        String ownerKey = "user:" + userId;

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey))
            .thenReturn(IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey));
        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(eq(order.getId()), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_1", "https://stripe.test/sess_1")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY);

        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(result.checkoutUrl()).isEqualTo("https://stripe.test/sess_1");
        assertThat(result.replayed()).isFalse();
        verify(cancelOrder, never()).cancelAndRestock(any());
        verify(idempotencyKeys).save(argThat(r -> r.isCompleted()
            && r.getOrderId().equals(order.getId()) && r.getCheckoutUrl().equals("https://stripe.test/sess_1")));
    }

    @Test
    void checkout_guestOwner_passesGuestEmailThrough() {
        UUID guestToken = UUID.randomUUID();
        CartOwner owner = CartOwner.ofGuest(guestToken);
        Order order = Order.createPendingPayment(null, "guest@example.com",
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        String ownerKey = "guest:" + guestToken;

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey))
            .thenReturn(IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey));
        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, "guest@example.com")).thenReturn(order);
        when(paymentPort.createCheckoutSession(
                eq(order.getId()), any(), anyString(), anyString(), anyString(), eq("guest@example.com")))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_2", "https://stripe.test/sess_2")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.checkout(owner, "guest@example.com", "https://ok", "https://cancel", IDEMPOTENCY_KEY);

        assertThat(result.orderId()).isEqualTo(order.getId());
        verify(reserveOrder).reserve(owner, "guest@example.com");
        verify(paymentPort).createCheckoutSession(
            eq(order.getId()), any(), anyString(), anyString(), anyString(), eq("guest@example.com"));
    }

    @Test
    void checkout_paymentNotConfigured_throwsWithoutReservingStockOrIdempotencyKey() {
        CartOwner owner = CartOwner.ofUser(UUID.randomUUID());
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.empty());
        when(processors.defaultProcessor()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalStateException.class);

        verify(reserveOrder, never()).reserve(any(), any());
        verify(idempotencyKeys, never()).reserve(any(), any(), any());
    }

    @Test
    void checkout_stripeCallFails_compensatesByCancellingOrderAndDeletingReservation() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        String ownerKey = "user:" + userId;
        IdempotencyRecord reservation = IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey);

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey)).thenReturn(reservation);
        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenThrow(new IllegalStateException("Stripe is down"));

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalStateException.class);

        verify(cancelOrder).cancelAndRestock(order.getId());
        verify(idempotencyKeys).delete(reservation);
    }

    @Test
    void checkout_circuitBreakerOpen_compensatesAndMapsToServiceUnavailableJustLikeAnyOtherAdapterFailure() {
        // CallNotPermittedException is what Resilience4j's @CircuitBreaker throws once a
        // payment adapter has failed enough to trip open — proves the existing
        // compensate-and-wrap-as-IllegalStateException path (already mapped to 503 by
        // GlobalExceptionHandler) handles it exactly like any other adapter RuntimeException,
        // with no special-casing needed.
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        String ownerKey = "user:" + userId;
        IdempotencyRecord reservation = IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey);
        var openCircuitBreaker = io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("stripe");

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey)).thenReturn(reservation);
        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenThrow(io.github.resilience4j.circuitbreaker.CallNotPermittedException
                .createCallNotPermittedException(openCircuitBreaker));

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalStateException.class);

        verify(cancelOrder).cancelAndRestock(order.getId());
        verify(idempotencyKeys).delete(reservation);
    }

    @Test
    void checkout_withCompletedIdempotencyKey_replaysStoredResultWithoutReReserving() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        String ownerKey = "user:" + userId;
        UUID originalOrderId = UUID.randomUUID();
        IdempotencyRecord record = IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey);
        record.completeCheckout(originalOrderId, "https://stripe.test/sess_1");

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.of(record));

        var result = service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY);

        assertThat(result.orderId()).isEqualTo(originalOrderId);
        assertThat(result.checkoutUrl()).isEqualTo("https://stripe.test/sess_1");
        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(reserveOrder, processors);
    }

    @Test
    void checkout_withInProgressIdempotencyKey_throwsConflict() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        String ownerKey = "user:" + userId;
        IdempotencyRecord inProgress = IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, ownerKey);

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void checkout_withStaleInProgressIdempotencyKey_retriesAndCompletes() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        String ownerKey = "user:" + userId;
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        IdempotencyRecord stale = staleRecord(IDEMPOTENCY_KEY, ownerKey);

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.of(stale));
        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_1", "https://stripe.test/sess_1")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY);

        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(stale.isCompleted()).isTrue();
        verify(idempotencyKeys, never()).reserve(any(), any(), any());
        verify(idempotencyKeys).save(stale);
    }

    @Test
    void checkout_withIdempotencyKeyUsedByAnotherOwner_throws() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        IdempotencyRecord usedByAnother =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, "user:" + UUID.randomUUID());
        usedByAnother.completeCheckout(UUID.randomUUID(), "https://stripe.test/sess_other");

        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT)).thenReturn(Optional.of(usedByAnother));

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handleWebhook_sessionCompleted_marksOrderPaidAndStoresShippingAddress() {
        Order order = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        order.attachCheckoutSession("sess_1", "stripe");
        ShippingAddress shippingAddress = new ShippingAddress(
            "Jane Angler", "123 Lake St", null, "Montreal", "QC", "H2X 1Y6", "CA");

        when(processors.byName("stripe")).thenReturn(paymentPort);
        when(paymentPort.verifyWebhook(anyString(), any())).thenReturn(Optional.of(
            new PaymentPort.PaymentEvent(PaymentPort.PaymentEventType.SESSION_COMPLETED, "sess_1", "pi_1", shippingAddress)));
        when(orderRepository.findByStripeSessionId("sess_1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean handled = service.handleWebhook("stripe", "{}", Map.of("stripe-signature", "sig"));

        assertThat(handled).isTrue();
        assertThat(order.getStatus().name()).isEqualTo("PAID");
        assertThat(order.getShippingAddress()).isEqualTo(shippingAddress);
        verify(orderEventPublisher).publishOrderPlaced(order);
    }

    @Test
    void handleWebhook_sessionExpired_cancelsAndRestocks() {
        Order order = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        order.attachCheckoutSession("sess_1", "stripe");

        when(processors.byName("stripe")).thenReturn(paymentPort);
        when(paymentPort.verifyWebhook(anyString(), any())).thenReturn(Optional.of(
            new PaymentPort.PaymentEvent(PaymentPort.PaymentEventType.SESSION_EXPIRED, "sess_1", null, null)));
        when(orderRepository.findByStripeSessionId("sess_1")).thenReturn(Optional.of(order));

        boolean handled = service.handleWebhook("stripe", "{}", Map.of("stripe-signature", "sig"));

        assertThat(handled).isTrue();
        verify(cancelOrder).cancelAndRestock(order.getId());
    }

    @Test
    void handleWebhook_invalidSignature_returnsFalse() {
        when(processors.byName("stripe")).thenReturn(paymentPort);
        when(paymentPort.verifyWebhook(anyString(), any())).thenReturn(Optional.empty());

        boolean handled = service.handleWebhook("stripe", "{}", Map.of());

        assertThat(handled).isFalse();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void handleWebhook_unknownProcessor_returnsFalse() {
        when(processors.byName("bitcoin")).thenThrow(new IllegalStateException("Unknown or unconfigured payment processor: bitcoin"));

        boolean handled = service.handleWebhook("bitcoin", "{}", Map.of());

        assertThat(handled).isFalse();
        verifyNoInteractions(orderRepository);
    }
}
