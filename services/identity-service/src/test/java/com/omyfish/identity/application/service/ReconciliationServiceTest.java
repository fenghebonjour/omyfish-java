package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.omyfish.identity.domain.port.out.PaymentPort.ReconciliationCandidate;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ReconciliationServiceTest {

    @Mock PaymentProcessorRegistry processors;
    @Mock SubscriptionRepository subscriptions;
    @Mock BillingService billing;
    @Mock PaymentPort stripe;
    @Mock PaymentPort paypal;

    private static final UUID USER = UUID.randomUUID();

    private ReconciliationService service() {
        return new ReconciliationService(processors, subscriptions, billing);
    }

    @Test
    void mismatchedLink_getsRepairedAndStatusResynced() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(processors.configured()).thenReturn(List.of(stripe));
        Instant periodEnd = Instant.now().plusSeconds(86_400);
        when(stripe.listRecentSubscriptions(any())).thenReturn(List.of(new ReconciliationCandidate(
            "stripe", USER, "cus_123", "sub_456", "monthly", "active", periodEnd)));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service().reconcile(Instant.now().minusSeconds(3600));

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.repaired()).hasSize(1);
        assertThat(result.errors()).isEmpty();
        assertThat(sub.getStripeCustomerId()).isEqualTo("cus_123");
        assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_456");
        assertThat(sub.getPaymentProcessor()).isEqualTo("stripe");
        verify(billing).applyEvent(new PaymentEvent(
            null, "stripe", "subscription_updated", "cus_123", "sub_456", "active", periodEnd, null, null,
            "monthly"));
    }

    @Test
    void alreadyLinked_skipsRepairButStillResyncsStatus() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("stripe", "cus_123", "sub_456");
        when(processors.configured()).thenReturn(List.of(stripe));
        when(stripe.listRecentSubscriptions(any())).thenReturn(List.of(new ReconciliationCandidate(
            "stripe", USER, "cus_123", "sub_456", "monthly", "canceled", null)));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));

        var result = service().reconcile(Instant.now().minusSeconds(3600));

        assertThat(result.repaired()).isEmpty();
        verify(subscriptions, never()).save(any());
        verify(billing).applyEvent(any());
    }

    @Test
    void candidateWithNoUserId_recordedAsErrorAndSkipped() {
        when(processors.configured()).thenReturn(List.of(stripe));
        when(stripe.listRecentSubscriptions(any())).thenReturn(List.of(new ReconciliationCandidate(
            "stripe", null, "cus_999", "sub_999", "monthly", "active", null)));

        var result = service().reconcile(Instant.now().minusSeconds(3600));

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.repaired()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        verifyNoInteractions(subscriptions, billing);
    }

    @Test
    void oneProcessorFailing_doesNotStopTheOthers() {
        when(processors.configured()).thenReturn(List.of(stripe, paypal));
        when(stripe.name()).thenReturn("stripe");
        when(stripe.listRecentSubscriptions(any())).thenThrow(new IllegalStateException("Stripe is down"));

        Subscription sub = Subscription.startTrial(USER, 7);
        when(paypal.listRecentSubscriptions(any())).thenReturn(List.of(new ReconciliationCandidate(
            "paypal", USER, "payer_1", "I-sub-1", "yearly", "ACTIVE", null)));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service().reconcile(Instant.now().minusSeconds(3600));

        assertThat(result.errors()).anyMatch(e -> e.contains("stripe") && e.contains("Stripe is down"));
        assertThat(result.repaired()).hasSize(1);
        assertThat(result.checked()).isEqualTo(1);
    }

    @Test
    void noLocalSubscriptionYet_createsOneThenLinksIt() {
        when(processors.configured()).thenReturn(List.of(stripe));
        when(stripe.listRecentSubscriptions(any())).thenReturn(List.of(new ReconciliationCandidate(
            "stripe", USER, "cus_123", "sub_456", "monthly", "active", null)));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.empty());
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service().reconcile(Instant.now().minusSeconds(3600));

        assertThat(result.repaired()).hasSize(1);
        verify(subscriptions, times(2)).save(any()); // the fresh trial row, then the attached one
    }
}
