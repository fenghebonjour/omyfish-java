package com.omyfish.identity.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Payment provider boundary (Stripe, PayPal, Adyen). Empty results mean "not configured". */
public interface PaymentPort {

    /** Stable id for this adapter, e.g. "stripe" — used to route refunds/webhooks/dedup. */
    String name();

    Optional<SubscriptionIntent> createSubscriptionIntent(
        UUID userId, String email, String plan, String idempotencyKey);

    /** Tokenizes a future off-session payment method for the customer. */
    Optional<SetupIntentResult> createSetupIntent(UUID userId, String email);

    /** Makes the given payment method the customer's default for future invoices. */
    void setDefaultPaymentMethod(String customerId, String paymentMethodId);

    /**
     * Full refund when amountCents is null, partial otherwise. Empty if nothing to refund.
     * lastPaymentReference is the processor-specific id of the subscription's last captured
     * payment (e.g. Adyen's pspReference); Stripe/PayPal ignore it and re-derive the latest
     * payment live from their own subscription object instead.
     */
    Optional<RefundResult> refundSubscription(
        String subscriptionId, String lastPaymentReference, Long amountCents, String idempotencyKey);

    /**
     * Verifies the webhook signature and maps the event; empty if invalid/unconfigured.
     * headers keys are lower-cased by the caller (different processors sign over different,
     * differently-named headers).
     */
    Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers);

    boolean isConfigured();

    /**
     * Subscriptions this processor created on/after `since` — used by reconciliation to repair
     * a local row whose processor-id link never got saved (e.g. a crash between the processor
     * call succeeding and subscriptions.save()). Not every processor supports this; one that
     * doesn't (or isn't live yet) returns an empty list rather than throwing.
     */
    List<ReconciliationCandidate> listRecentSubscriptions(Instant since);

    record ReconciliationCandidate(
        String processor,
        UUID userId,            // null if the processor-side metadata is missing/unparseable
        String customerId,
        String subscriptionId,
        String plan,
        String providerStatus,
        Instant periodEnd
    ) {}

    record PaymentEvent(
        String eventId,          // provider's event id, used to dedup redelivered webhooks
        String processor,        // which adapter verified this event, e.g. "stripe"
        String type,             // subscription_updated | subscription_deleted | payment_method_attached | payment_captured
        String customerId,
        String subscriptionId,
        String providerStatus,   // e.g. canceled / unpaid / incomplete_expired / active
        Instant periodEnd,
        String paymentMethodId,  // set for payment_method_attached
        String paymentReference  // set for payment_captured (e.g. Adyen's pspReference)
    ) {}

    record SubscriptionIntent(
        String processor,
        String customerId,
        String subscriptionId,
        String clientSecret,     // Stripe: client secret. PayPal/Adyen: approval URL / session blob — see adapter docs
        String status
    ) {}

    record SetupIntentResult(
        String processor,
        String customerId,
        String clientSecret
    ) {}

    record RefundResult(
        String refundId,
        String status,
        Long amountCents
    ) {}
}
