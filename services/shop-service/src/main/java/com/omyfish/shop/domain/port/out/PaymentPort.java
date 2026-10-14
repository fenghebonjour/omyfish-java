package com.omyfish.shop.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// One-time-payment port for Omyshop checkout — deliberately separate from identity-service's
// subscription-shaped PaymentPort (fixed monthly/yearly prices); this one needs arbitrary,
// per-cart line items and a one-shot refund rather than recurring billing.
public interface PaymentPort {

    record LineItem(String name, long unitPriceCents, int quantity) {}

    record CheckoutSession(String sessionId, String checkoutUrl) {}

    record RefundResult(String refundId, String status, Long amountCents) {}

    enum PaymentEventType { SESSION_COMPLETED, SESSION_EXPIRED }

    record PaymentEvent(
        PaymentEventType type, String stripeSessionId, String stripePaymentIntentId
    ) {}

    boolean isConfigured();

    Optional<CheckoutSession> createCheckoutSession(
        UUID orderId, List<LineItem> lines, String successUrl, String cancelUrl, String idempotencyKey
    );

    Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey);

    Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers);
}
