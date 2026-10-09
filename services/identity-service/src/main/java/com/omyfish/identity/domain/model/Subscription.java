package com.omyfish.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "identity")
public class Subscription {

    public static final String TRIALING = "trialing";
    public static final String ACTIVE = "active";
    public static final String CANCELED = "canceled";
    public static final String EXPIRED = "expired";
    public static final String PAST_DUE = "past_due";

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status;

    private String plan; // monthly | yearly

    private Instant trialEnd;
    private Instant currentPeriodEnd;
    // Processor-agnostic despite the name: every adapter (Stripe/PayPal/Adyen) writes its own
    // customer/subscription id here. Not renamed to avoid a column/call-site rename with no
    // behavioral upside; paymentProcessor below records which processor's ids these are.
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String paymentProcessor;
    private String lastPaymentReference;
    private Instant createdAt;
    private Instant updatedAt;

    protected Subscription() {}

    public static Subscription startTrial(UUID userId, int trialDays) {
        Subscription s = new Subscription();
        s.id = UUID.randomUUID();
        s.userId = userId;
        s.status = TRIALING;
        s.trialEnd = Instant.now().plus(trialDays, ChronoUnit.DAYS);
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    /** A trial past its end date reads as expired without needing a write. */
    public String getEffectiveStatus() {
        if (TRIALING.equals(status) && trialEnd != null && trialEnd.isBefore(Instant.now())) {
            return EXPIRED;
        }
        return status;
    }

    public void activate(String plan, Instant periodEnd,
                         String stripeCustomerId, String stripeSubscriptionId) {
        this.status = ACTIVE;
        this.plan = plan;
        this.currentPeriodEnd = periodEnd;
        if (stripeCustomerId != null) this.stripeCustomerId = stripeCustomerId;
        if (stripeSubscriptionId != null) this.stripeSubscriptionId = stripeSubscriptionId;
        this.updatedAt = Instant.now();
    }

    /** Records a processor's ids as soon as they're known, without changing status. */
    public void attachProcessor(String processor, String customerId, String subscriptionId) {
        this.paymentProcessor = processor;
        this.stripeCustomerId = customerId;
        this.stripeSubscriptionId = subscriptionId;
        this.updatedAt = Instant.now();
    }

    /** Records the processor customer id alone, e.g. after tokenizing a payment method pre-checkout. */
    public void attachProcessorCustomerId(String processor, String customerId) {
        this.paymentProcessor = processor;
        this.stripeCustomerId = customerId;
        this.updatedAt = Instant.now();
    }

    /** Records the reference needed to refund the most recently captured payment (e.g. Adyen's pspReference). */
    public void recordPayment(String reference) {
        this.lastPaymentReference = reference;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = CANCELED;
        this.updatedAt = Instant.now();
    }

    /** A renewal charge failed but the processor is still retrying — access isn't pulled yet. */
    public void markPastDue() {
        this.status = PAST_DUE;
        this.updatedAt = Instant.now();
    }

    public void extendTrial(int days) {
        Instant baseline = trialEnd != null && trialEnd.isAfter(Instant.now())
            ? trialEnd : Instant.now();
        this.status = TRIALING;
        this.trialEnd = baseline.plus(days, ChronoUnit.DAYS);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPlan() { return plan; }
    public Instant getTrialEnd() { return trialEnd; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public String getPaymentProcessor() { return paymentProcessor; }
    public String getLastPaymentReference() { return lastPaymentReference; }
    public Instant getCreatedAt() { return createdAt; }
}
