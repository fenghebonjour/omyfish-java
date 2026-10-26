package com.omyfish.shared.stripe;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Low-level Stripe-calling plumbing shared by every Stripe adapter in this platform
 * (identity-service's subscriptions, shop-service's one-shot checkout) — BACKLOG.md item M.
 * Each adapter constructs its own instance with its own secret key; this is not a Spring bean.
 */
public class StripeHttpSupport {

    private static final Logger log = LoggerFactory.getLogger(StripeHttpSupport.class);

    // A slow (not down) Stripe must not hang a request indefinitely. No retries: a failed
    // charge/subscription-create/checkout-session-create must never be retried without an
    // idempotency key already in place, so retrying here would risk a double-charge — each
    // caller is responsible for its own idempotency-key wiring on top of this.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final String secretKey;

    public StripeHttpSupport(String secretKey) {
        this.secretKey = secretKey;
    }

    public RequestOptions.RequestOptionsBuilder baseOptions() {
        return RequestOptions.builder()
            .setApiKey(secretKey)
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setReadTimeout(READ_TIMEOUT_MS);
    }

    // getObject() comes back empty whenever the event's api_version doesn't match this SDK's
    // compiled version (BACKLOG item L) — common in sandboxes, not just malformed payloads.
    // deserializeUnsafe() ignores that version check; it only fails for a genuinely incompatible
    // schema, which EventDataObjectDeserializationException still catches below.
    public <T extends StripeObject> T deserializeEventObject(Event event, Class<T> type) {
        Optional<StripeObject> safe = event.getDataObjectDeserializer().getObject();
        if (safe.isPresent()) {
            return type.cast(safe.get());
        }
        log.warn("Stripe webhook {} ({}): getObject() returned empty — likely event api_version "
                + "mismatch against the stripe-java SDK's compiled version (BACKLOG item L); "
                + "falling back to deserializeUnsafe()", event.getId(), event.getType());
        try {
            return type.cast(event.getDataObjectDeserializer().deserializeUnsafe());
        } catch (EventDataObjectDeserializationException e) {
            log.warn("Stripe webhook {} ({}): deserializeUnsafe() also failed: {}",
                    event.getId(), event.getType(), e.getMessage());
            return null;
        }
    }
}
