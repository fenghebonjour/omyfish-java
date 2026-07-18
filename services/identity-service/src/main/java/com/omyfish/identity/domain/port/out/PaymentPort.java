package com.omyfish.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Payment provider boundary (Stripe). Empty results mean "not configured". */
public interface PaymentPort {

    Optional<String> createCheckoutUrl(UUID userId, String email, String plan);

    /** Verifies the webhook signature and maps the event; empty if invalid/unconfigured. */
    Optional<PaymentEvent> verifyWebhook(String payload, String signature);

    boolean isConfigured();

    record PaymentEvent(
        String type,             // checkout_completed | subscription_updated | subscription_deleted
        String userId,           // set for checkout_completed
        String plan,             // set for checkout_completed
        String customerId,
        String subscriptionId,
        String providerStatus,   // e.g. canceled / unpaid / active
        Instant periodEnd
    ) {}
}
