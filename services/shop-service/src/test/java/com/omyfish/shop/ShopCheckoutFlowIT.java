package com.omyfish.shop;

import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shop.domain.port.in.AddToCartUseCase;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
import com.omyfish.shop.domain.port.in.GetOrderUseCase;
import com.omyfish.shop.domain.port.out.PaymentPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end check against a real Postgres (with the V1 seed migration's real product rows) and
 * a mocked PaymentPort standing in for Stripe — no real network call is made, but the
 * reserve-stock / create-order / confirm-webhook wiring all runs through the real Spring
 * context exactly as it would in production.
 */
@SpringBootTest
@Testcontainers
class ShopCheckoutFlowIT {

    // One of the ten products seeded by V1__create_products_table.sql.
    private static final UUID SEEDED_PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AddToCartUseCase addToCartUseCase;
    @Autowired CheckoutUseCase checkoutUseCase;
    @Autowired ConfirmPaymentWebhookUseCase confirmPaymentWebhookUseCase;
    @Autowired GetOrderUseCase getOrderUseCase;

    @MockBean PaymentPort paymentPort;

    @Test
    void checkoutThenWebhook_movesOrderFromPendingPaymentToPaid() {
        UUID userId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        addToCartUseCase.addItem(owner, SEEDED_PRODUCT_ID, 2);

        when(paymentPort.isConfigured()).thenReturn(true);
        when(paymentPort.createCheckoutSession(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.of(new PaymentPort.CheckoutSession("sess_it_1", "https://stripe.test/sess_it_1")));

        var checkoutResult = checkoutUseCase.checkout(owner, null, "https://ok", "https://cancel");
        assertThat(getOrderUseCase.get(checkoutResult.orderId(), userId).getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);

        ShippingAddress shippingAddress = new ShippingAddress(
            "Jane Angler", "123 Lake St", null, "Montreal", "QC", "H2X 1Y6", "CA");
        when(paymentPort.verifyWebhook(anyString(), any())).thenReturn(Optional.of(
            new PaymentPort.PaymentEvent(
                PaymentPort.PaymentEventType.SESSION_COMPLETED, "sess_it_1", "pi_it_1", shippingAddress)));

        boolean handled = confirmPaymentWebhookUseCase.handleWebhook("{}", Map.of("stripe-signature", "sig"));

        assertThat(handled).isTrue();
        var paidOrder = getOrderUseCase.get(checkoutResult.orderId(), userId);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getStripePaymentIntentId()).isEqualTo("pi_it_1");
        assertThat(paidOrder.getShippingAddress()).isEqualTo(shippingAddress);
    }
}
