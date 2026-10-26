package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @InjectMocks CheckoutOrchestrationService service;

    @Test
    void checkout_happyPath_attachesSessionAndReturnsCheckoutUrl() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));

        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(eq(order.getId()), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_1", "https://stripe.test/sess_1")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.checkout(owner, null, "https://ok", "https://cancel");

        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(result.checkoutUrl()).isEqualTo("https://stripe.test/sess_1");
        verify(cancelOrder, never()).cancelAndRestock(any());
    }

    @Test
    void checkout_guestOwner_passesGuestEmailThrough() {
        UUID guestToken = UUID.randomUUID();
        CartOwner owner = CartOwner.ofGuest(guestToken);
        Order order = Order.createPendingPayment(null, "guest@example.com",
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));

        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, "guest@example.com")).thenReturn(order);
        when(paymentPort.createCheckoutSession(
                eq(order.getId()), any(), anyString(), anyString(), anyString(), eq("guest@example.com")))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_2", "https://stripe.test/sess_2")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.checkout(owner, "guest@example.com", "https://ok", "https://cancel");

        assertThat(result.orderId()).isEqualTo(order.getId());
        verify(reserveOrder).reserve(owner, "guest@example.com");
        verify(paymentPort).createCheckoutSession(
            eq(order.getId()), any(), anyString(), anyString(), anyString(), eq("guest@example.com"));
    }

    @Test
    void checkout_paymentNotConfigured_throwsWithoutReservingStock() {
        CartOwner owner = CartOwner.ofUser(UUID.randomUUID());
        when(processors.defaultProcessor()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel"))
            .isInstanceOf(IllegalStateException.class);

        verify(reserveOrder, never()).reserve(any(), any());
    }

    @Test
    void checkout_stripeCallFails_compensatesByCancellingOrder() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        Order order = Order.createPendingPayment(userId, null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));

        when(processors.defaultProcessor()).thenReturn(Optional.of(paymentPort));
        lenient().when(paymentPort.name()).thenReturn("stripe");
        when(reserveOrder.reserve(owner, null)).thenReturn(order);
        when(paymentPort.createCheckoutSession(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenThrow(new IllegalStateException("Stripe is down"));

        assertThatThrownBy(() -> service.checkout(owner, null, "https://ok", "https://cancel"))
            .isInstanceOf(IllegalStateException.class);

        verify(cancelOrder).cancelAndRestock(order.getId());
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
