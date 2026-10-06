package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.model.User;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;
import com.omyfish.identity.domain.port.out.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BillingService {

    public static final int TRIAL_DAYS = 7;
    public static final double MONTHLY_CAD = 5;
    public static final double YEARLY_CAD = 29;

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final PaymentPort payments;

    public BillingService(SubscriptionRepository subscriptions, UserRepository users,
                          PaymentPort payments) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.payments = payments;
    }

    public Subscription startTrial(UUID userId) {
        return subscriptions.findByUserId(userId)
            .orElseGet(() -> subscriptions.save(Subscription.startTrial(userId, TRIAL_DAYS)));
    }

    public Subscription mySubscription(UUID userId) {
        return startTrial(userId);
    }

    /** Empty when Stripe is not configured. */
    public Optional<PaymentPort.SubscriptionIntent> startCheckout(UUID userId, String plan) {
        if (!plan.equals("monthly") && !plan.equals("yearly")) {
            throw new IllegalArgumentException("plan must be monthly or yearly");
        }
        User user = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<PaymentPort.SubscriptionIntent> intent =
            payments.createSubscriptionIntent(userId, user.getEmail(), plan);
        intent.ifPresent(i -> {
            Subscription sub = startTrial(userId);
            sub.attachStripeIds(i.customerId(), i.subscriptionId());
            subscriptions.save(sub);
        });
        return intent;
    }

    /** Empty when Stripe is not configured. */
    public Optional<PaymentPort.SetupIntentResult> startPaymentMethodSetup(UUID userId) {
        User user = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<PaymentPort.SetupIntentResult> intent =
            payments.createSetupIntent(userId, user.getEmail());
        intent.ifPresent(i -> {
            Subscription sub = startTrial(userId);
            sub.attachStripeCustomerId(i.customerId());
            subscriptions.save(sub);
        });
        return intent;
    }

    public PaymentPort.RefundResult refund(UUID userId, Long amountCents) {
        Subscription sub = subscriptions.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No subscription for that user"));
        if (sub.getStripeSubscriptionId() == null) {
            throw new IllegalArgumentException("No Stripe subscription on file");
        }
        return payments.refundSubscription(sub.getStripeSubscriptionId(), amountCents)
            .orElseThrow(() -> new IllegalStateException(
                "Stripe is not configured or there is nothing to refund"));
    }

    public boolean applyEvent(PaymentEvent event) {
        switch (event.type()) {
            case "subscription_updated", "subscription_deleted" -> {
                Optional<Subscription> found =
                    subscriptions.findByStripeCustomerId(event.customerId());
                if (found.isEmpty()) return false;
                Subscription sub = found.get();
                if (event.type().equals("subscription_deleted")
                    || "canceled".equals(event.providerStatus())
                    || "unpaid".equals(event.providerStatus())
                    || "incomplete_expired".equals(event.providerStatus())) {
                    sub.cancel();
                } else {
                    sub.activate(sub.getPlan() != null ? sub.getPlan() : "monthly",
                        event.periodEnd(), null, event.subscriptionId());
                }
                subscriptions.save(sub);
                return true;
            }
            case "payment_method_attached" -> {
                payments.setDefaultPaymentMethod(event.customerId(), event.paymentMethodId());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    public List<Subscription> allSubscriptions() {
        return subscriptions.findAll();
    }

    public Stats stats() {
        List<Subscription> all = subscriptions.findAll();
        long trialing = count(all, Subscription.TRIALING);
        long active = count(all, Subscription.ACTIVE);
        long canceled = count(all, Subscription.CANCELED);
        long expired = count(all, Subscription.EXPIRED);
        long monthly = all.stream().filter(s ->
            Subscription.ACTIVE.equals(s.getEffectiveStatus())
                && "monthly".equals(s.getPlan())).count();
        long yearly = all.stream().filter(s ->
            Subscription.ACTIVE.equals(s.getEffectiveStatus())
                && "yearly".equals(s.getPlan())).count();
        double mrr = monthly * MONTHLY_CAD + yearly * YEARLY_CAD / 12;
        return new Stats(trialing, active, canceled, expired, monthly, yearly,
            Math.round(mrr * 100) / 100.0);
    }

    public Subscription grant(UUID userId, String plan, int days) {
        Subscription sub = startTrial(userId);
        sub.activate(plan, Instant.now().plus(days, ChronoUnit.DAYS), null, null);
        return subscriptions.save(sub);
    }

    public Subscription revoke(UUID userId) {
        Subscription sub = subscriptions.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No subscription for that user"));
        sub.cancel();
        return subscriptions.save(sub);
    }

    public Subscription extendTrial(UUID userId, int days) {
        Subscription sub = startTrial(userId);
        sub.extendTrial(days);
        return subscriptions.save(sub);
    }

    private static long count(List<Subscription> all, String status) {
        return all.stream().filter(s -> status.equals(s.getEffectiveStatus())).count();
    }

    public record Stats(long trialing, long active, long canceled, long expired,
                        long activeMonthly, long activeYearly, double mrrCad) {}
}
