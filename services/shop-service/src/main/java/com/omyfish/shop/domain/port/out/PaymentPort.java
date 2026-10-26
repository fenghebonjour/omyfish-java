package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.valueobject.ShippingAddress;

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

    // shippingAddress is populated for SESSION_COMPLETED (Checkout always collects it — see
    // StripeCheckoutAdapter.createCheckoutSession) and null for SESSION_EXPIRED.
    record PaymentEvent(
        PaymentEventType type, String stripeSessionId, String stripePaymentIntentId,
        ShippingAddress shippingAddress
    ) {}

    /** Stable id for this adapter, e.g. "stripe" — used to route refunds/webhooks by processor. */
    String name();

    boolean isConfigured();

    // customerEmail pre-fills/locks Stripe's own email field — pass the guest's email for a
    // guest checkout so they aren't asked to type it twice; null for an account checkout.
    Optional<CheckoutSession> createCheckoutSession(
        UUID orderId, List<LineItem> lines, String successUrl, String cancelUrl, String idempotencyKey,
        String customerEmail
    );

    Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey);

    Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers);
}
