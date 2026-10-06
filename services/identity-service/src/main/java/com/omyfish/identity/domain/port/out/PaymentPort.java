package com.omyfish.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Payment provider boundary (Stripe). Empty results mean "not configured". */
public interface PaymentPort {

    Optional<SubscriptionIntent> createSubscriptionIntent(UUID userId, String email, String plan);

    /** Verifies the webhook signature and maps the event; empty if invalid/unconfigured. */
    Optional<PaymentEvent> verifyWebhook(String payload, String signature);

    boolean isConfigured();

    record PaymentEvent(
        String type,             // subscription_updated | subscription_deleted
        String customerId,
        String subscriptionId,
        String providerStatus,   // e.g. canceled / unpaid / incomplete_expired / active
        Instant periodEnd
    ) {}

    record SubscriptionIntent(
        String customerId,
        String subscriptionId,
        String clientSecret,
        String status
    ) {}
}
