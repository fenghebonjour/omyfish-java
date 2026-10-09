package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.omyfish.identity.domain.port.out.PaymentPort.ReconciliationCandidate;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Repairs a local Subscription row whose processor-id link never got saved — e.g. a crash
 * between a processor call succeeding in BillingService.startCheckout/startPaymentMethodSetup
 * and subscriptions.save() persisting the id. Driven from each configured processor's own
 * subscription list (keyed by the user_id metadata every checkout already attaches), not by
 * scanning local rows for a missing link, since a null link is also just the normal state for
 * any trialing user who hasn't subscribed yet.
 *
 * Single-instance assumption, same as observation-service's OutboxPublisherJob: no claim/lock
 * step, so a second replica would run this concurrently. Lower-stakes than outbox's own version
 * of this caveat, since relinking + resyncing status here is idempotent either way.
 */
public class ReconciliationService {

    private final PaymentProcessorRegistry processors;
    private final SubscriptionRepository subscriptions;
    private final BillingService billing;

    public ReconciliationService(
        PaymentProcessorRegistry processors, SubscriptionRepository subscriptions, BillingService billing
    ) {
        this.processors = processors;
        this.subscriptions = subscriptions;
        this.billing = billing;
    }

    public ReconciliationResult reconcile(Instant since) {
        List<String> repaired = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int checked = 0;

        for (PaymentPort processor : processors.configured()) {
            List<ReconciliationCandidate> candidates;
            try {
                candidates = processor.listRecentSubscriptions(since);
            } catch (RuntimeException e) {
                errors.add(processor.name() + ": failed to list subscriptions — " + e.getMessage());
                continue;
            }
            for (ReconciliationCandidate c : candidates) {
                checked++;
                if (c.userId() == null) {
                    errors.add(c.processor() + " subscription " + c.subscriptionId()
                        + " has no usable user_id metadata, skipped");
                    continue;
                }
                try {
                    if (relink(c)) {
                        repaired.add(c.processor() + ":" + c.subscriptionId() + " -> user " + c.userId());
                    }
                    resyncStatus(c);
                } catch (RuntimeException e) {
                    errors.add(c.processor() + " subscription " + c.subscriptionId() + ": " + e.getMessage());
                }
            }
        }
        return new ReconciliationResult(checked, repaired, errors);
    }

    /** Returns true if the local row's link was actually missing/mismatched and got repaired. */
    private boolean relink(ReconciliationCandidate c) {
        Subscription sub = subscriptions.findByUserId(c.userId())
            .orElseGet(() -> subscriptions.save(Subscription.startTrial(c.userId(), 0)));
        boolean mismatched = !Objects.equals(sub.getStripeCustomerId(), c.customerId())
            || !Objects.equals(sub.getStripeSubscriptionId(), c.subscriptionId());
        if (mismatched) {
            sub.attachProcessor(c.processor(), c.customerId(), c.subscriptionId());
            subscriptions.save(sub);
        }
        return mismatched;
    }

    /**
     * Re-syncs status/periodEnd via the already-tested webhook-effect path, now that the link
     * is guaranteed to resolve. A bonus beyond strict link-repair (catches any other missed
     * webhook too) at near-zero extra cost, since this data's already in hand from the list
     * call. eventId is null so applyEvent's dedup check doesn't apply — safe, since
     * applyEventEffects's subscription_updated case just re-asserts current status.
     */
    private void resyncStatus(ReconciliationCandidate c) {
        billing.applyEvent(new PaymentEvent(
            null, c.processor(), "subscription_updated",
            c.customerId(), c.subscriptionId(), c.providerStatus(), c.periodEnd(), null, null, c.plan()));
    }

    public record ReconciliationResult(int checked, List<String> repaired, List<String> errors) {}
}
