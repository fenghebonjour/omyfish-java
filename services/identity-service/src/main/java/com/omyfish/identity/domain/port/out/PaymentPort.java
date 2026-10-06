package com.omyfish.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Payment provider boundary (Stripe). Empty results mean "not configured". */
public interface PaymentPort {

    Optional<SubscriptionIntent> createSubscriptionIntent(UUID userId, String email, String plan);

    /** Tokenizes a future off-session payment method for the customer. */
    Optional<SetupIntentResult> createSetupIntent(UUID userId, String email);

    /** Makes the given payment method the customer's default for future invoices. */
    void setDefaultPaymentMethod(String customerId, String paymentMethodId);

    /** Full refund when amountCents is null, partial otherwise. Empty if nothing to refund. */
    Optional<RefundResult> refundSubscription(String stripeSubscriptionId, Long amountCents);

    /** Verifies the webhook signature and maps the event; empty if invalid/unconfigured. */
    Optional<PaymentEvent> verifyWebhook(String payload, String signature);

    boolean isConfigured();

    record PaymentEvent(
        String type,             // subscription_updated | subscription_deleted | payment_method_attached
        String customerId,
        String subscriptionId,
        String providerStatus,   // e.g. canceled / unpaid / incomplete_expired / active
        Instant periodEnd,
        String paymentMethodId   // set for payment_method_attached
    ) {}

    record SubscriptionIntent(
        String customerId,
        String subscriptionId,
        String clientSecret,
        String status
    ) {}

    record SetupIntentResult(
        String customerId,
        String clientSecret
    ) {}

    record RefundResult(
        String refundId,
        String status,
        Long amountCents
    ) {}
}
