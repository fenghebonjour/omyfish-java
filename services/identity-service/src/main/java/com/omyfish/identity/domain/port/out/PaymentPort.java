package com.omyfish.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Payment provider boundary (Stripe). */
public interface PaymentPort {

    /** Empty when the provider is not configured. */
    Optional<String> createCheckoutUrl(UUID userId, String email, String plan);

    /**
     * Verifies the webhook signature and maps the event; empty when the event is
     * authentic but of a type this service does not act on.
     *
     * @throws com.omyfish.identity.domain.exception.WebhookVerificationException
     *         if the signature is invalid or the payload cannot be parsed
     * @throws IllegalStateException if webhook verification is not configured
     */
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
