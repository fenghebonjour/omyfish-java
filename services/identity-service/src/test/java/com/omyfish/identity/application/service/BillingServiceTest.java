package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;
import com.omyfish.identity.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock UserRepository users;
    @Mock PaymentPort payments;
    @InjectMocks BillingService billing;

    private static final UUID USER = UUID.randomUUID();

    @Test
    void startTrial_isIdempotent() {
        Subscription existing = Subscription.startTrial(USER, 7);
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(existing));

        assertThat(billing.startTrial(USER)).isSameAs(existing);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void trialExpiry_readsAsExpired() {
        Subscription expired = Subscription.startTrial(USER, -1);
        assertThat(expired.getEffectiveStatus()).isEqualTo(Subscription.EXPIRED);
    }

    @Test
    void checkoutCompletedEvent_activatesSubscription() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean handled = billing.applyEvent(new PaymentEvent(
            "checkout_completed", USER.toString(), "yearly",
            "cus_123", "sub_456", null, null));

        assertThat(handled).isTrue();
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.ACTIVE);
        assertThat(sub.getPlan()).isEqualTo("yearly");
        assertThat(sub.getStripeCustomerId()).isEqualTo("cus_123");
    }

    @Test
    void subscriptionDeletedEvent_cancels() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.activate("monthly", null, "cus_123", "sub_456");
        when(subscriptions.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean handled = billing.applyEvent(new PaymentEvent(
            "subscription_deleted", null, null, "cus_123", "sub_456", null, null));

        assertThat(handled).isTrue();
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.CANCELED);
    }

    @Test
    void subscriptionUpdatedEvent_refreshesPeriodEnd() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.activate("monthly", null, "cus_123", "sub_456");
        when(subscriptions.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Instant periodEnd = Instant.now().plusSeconds(30 * 86400);

        billing.applyEvent(new PaymentEvent(
            "subscription_updated", null, null, "cus_123", "sub_456", "active", periodEnd));

        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.ACTIVE);
    }

    @Test
    void eventForUnknownCustomer_notHandled() {
        when(subscriptions.findByStripeCustomerId("cus_ghost")).thenReturn(Optional.empty());

        assertThat(billing.applyEvent(new PaymentEvent(
            "subscription_updated", null, null, "cus_ghost", null, "active", null))).isFalse();
    }

    @Test
    void stats_computesMrrFromActivePlans() {
        Subscription monthly = Subscription.startTrial(UUID.randomUUID(), 7);
        monthly.activate("monthly", null, null, null);
        Subscription yearly = Subscription.startTrial(UUID.randomUUID(), 7);
        yearly.activate("yearly", null, null, null);
        Subscription trialing = Subscription.startTrial(UUID.randomUUID(), 7);
        when(subscriptions.findAll()).thenReturn(List.of(monthly, yearly, trialing));

        BillingService.Stats stats = billing.stats();

        assertThat(stats.active()).isEqualTo(2);
        assertThat(stats.trialing()).isEqualTo(1);
        assertThat(stats.mrrCad()).isEqualTo(Math.round((5 + 29 / 12.0) * 100) / 100.0);
    }

    @Test
    void grant_activatesWithoutStripe() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription granted = billing.grant(USER, "yearly", 365);

        assertThat(granted.getEffectiveStatus()).isEqualTo(Subscription.ACTIVE);
        assertThat(granted.getCurrentPeriodEnd()).isAfter(Instant.now());
    }
}
