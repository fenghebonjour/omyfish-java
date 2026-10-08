package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.IdempotencyRecord;
import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.IdempotencyConflictException;
import com.omyfish.identity.domain.port.out.IdempotencyKeyRepository;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.omyfish.identity.domain.port.out.ProcessedWebhookEventRepository;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;
import com.omyfish.identity.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock UserRepository users;
    @Mock PaymentPort payments;
    @Mock PaymentPort paypalPayments;
    @Mock PaymentProcessorRegistry processors;
    @Mock IdempotencyKeyRepository idempotencyKeys;
    @Mock ProcessedWebhookEventRepository processedWebhookEvents;
    @InjectMocks BillingService billing;

    private static final UUID USER = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    /** An incomplete reservation old enough for BillingService to treat as orphaned, not in-progress. */
    private static IdempotencyRecord staleRecord(String key, String endpoint, UUID userId) {
        IdempotencyRecord record = IdempotencyRecord.reserve(key, endpoint, userId);
        ReflectionTestUtils.setField(record, "createdAt", Instant.now().minus(Duration.ofMinutes(5)));
        return record;
    }

    @BeforeEach
    void setUpDefaultProcessor() {
        lenient().when(processors.defaultProcessor()).thenReturn(Optional.of(payments));
        lenient().when(processors.byName(anyString())).thenReturn(payments);
        lenient().when(payments.name()).thenReturn("stripe");
    }

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
    void startCheckout_attachesStripeIdsWithoutActivating() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(users.findById(USER)).thenReturn(Optional.of(
            com.omyfish.identity.domain.model.User.create(
                "angler@example.com", "hash", "user")));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER))
            .thenReturn(IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER));
        when(payments.createSubscriptionIntent(USER, "angler@example.com", "yearly", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PaymentPort.SubscriptionIntent(
                "stripe", "cus_123", "sub_456", "secret_abc", "incomplete")));

        Optional<PaymentPort.SubscriptionIntent> intent =
            billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY);

        assertThat(intent).isPresent();
        assertThat(intent.get().clientSecret()).isEqualTo("secret_abc");
        assertThat(sub.getStripeCustomerId()).isEqualTo("cus_123");
        assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_456");
        assertThat(sub.getPaymentProcessor()).isEqualTo("stripe");
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.TRIALING);
        verify(idempotencyKeys).save(any());
    }

    @Test
    void startCheckout_whenNoProcessorConfigured_returnsEmptyWithoutReservingIdempotencyKey() {
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.empty());
        when(processors.defaultProcessor()).thenReturn(Optional.empty());

        Optional<PaymentPort.SubscriptionIntent> intent =
            billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY);

        assertThat(intent).isEmpty();
        verifyNoInteractions(users);
        verify(idempotencyKeys, never()).reserve(any(), any(), any());
    }

    @Test
    void startCheckout_withCompletedIdempotencyKey_replaysStoredResultWithoutCallingStripe() {
        IdempotencyRecord record =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER);
        record.completeCheckout("cus_123", "sub_456", "secret_abc", "incomplete");
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.of(record));

        Optional<PaymentPort.SubscriptionIntent> intent =
            billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY);

        assertThat(intent).isPresent();
        assertThat(intent.get().clientSecret()).isEqualTo("secret_abc");
        verifyNoInteractions(payments);
        verify(users, never()).findById(any());
    }

    @Test
    void startCheckout_withInProgressIdempotencyKey_throwsConflict() {
        IdempotencyRecord inProgress =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER);
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void startCheckout_withStaleInProgressIdempotencyKey_retriesAndCompletes() {
        IdempotencyRecord stale = staleRecord(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER);
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.of(stale));
        when(users.findById(USER)).thenReturn(Optional.of(
            com.omyfish.identity.domain.model.User.create(
                "angler@example.com", "hash", "user")));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(Subscription.startTrial(USER, 7)));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.createSubscriptionIntent(USER, "angler@example.com", "yearly", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PaymentPort.SubscriptionIntent(
                "stripe", "cus_123", "sub_456", "secret_abc", "incomplete")));

        Optional<PaymentPort.SubscriptionIntent> intent =
            billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY);

        assertThat(intent).isPresent();
        assertThat(intent.get().clientSecret()).isEqualTo("secret_abc");
        assertThat(stale.isCompleted()).isTrue();
        verify(idempotencyKeys, never()).reserve(any(), any(), any());
        verify(idempotencyKeys).save(stale);
    }

    @Test
    void startCheckout_withStaleInProgressIdempotencyKeyUsedByAnotherUser_throws() {
        IdempotencyRecord stale =
            staleRecord(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, UUID.randomUUID());
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.of(stale));
        when(users.findById(USER)).thenReturn(Optional.of(
            com.omyfish.identity.domain.model.User.create(
                "angler@example.com", "hash", "user")));

        assertThatThrownBy(() -> billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void startCheckout_withIdempotencyKeyUsedByAnotherUser_throws() {
        IdempotencyRecord record = IdempotencyRecord.reserve(
            IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, UUID.randomUUID());
        record.completeCheckout("cus_123", "sub_456", "secret_abc", "incomplete");
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startCheckout_whenStripeCallFails_releasesReservationForRetry() {
        when(users.findById(USER)).thenReturn(Optional.of(
            com.omyfish.identity.domain.model.User.create(
                "angler@example.com", "hash", "user")));
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT))
            .thenReturn(Optional.empty());
        IdempotencyRecord reservation =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER);
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.CHECKOUT, USER))
            .thenReturn(reservation);
        when(payments.createSubscriptionIntent(USER, "angler@example.com", "yearly", IDEMPOTENCY_KEY))
            .thenThrow(new IllegalStateException("Stripe checkout failed: boom"));

        assertThatThrownBy(() -> billing.startCheckout(USER, "yearly", IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalStateException.class);

        verify(idempotencyKeys).delete(reservation);
    }

    @Test
    void subscriptionUpdatedEvent_incompleteExpired_cancels() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("stripe", "cus_123", "sub_456");
        when(subscriptions.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean handled = billing.applyEvent(new PaymentEvent(
            "evt_1", "stripe", "subscription_updated", "cus_123", "sub_456", "incomplete_expired",
            null, null, null));

        assertThat(handled).isTrue();
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.CANCELED);
        verify(processedWebhookEvents).save(any());
    }

    @Test
    void subscriptionDeletedEvent_cancels() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.activate("monthly", null, "cus_123", "sub_456");
        when(subscriptions.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean handled = billing.applyEvent(new PaymentEvent(
            "evt_2", "stripe", "subscription_deleted", "cus_123", "sub_456", null,
            null, null, null));

        assertThat(handled).isTrue();
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.CANCELED);
        verify(processedWebhookEvents).save(any());
    }

    @Test
    void subscriptionUpdatedEvent_refreshesPeriodEnd() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.activate("monthly", null, "cus_123", "sub_456");
        when(subscriptions.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Instant periodEnd = Instant.now().plusSeconds(30 * 86400);

        billing.applyEvent(new PaymentEvent(
            "evt_3", "stripe", "subscription_updated", "cus_123", "sub_456", "active",
            periodEnd, null, null));

        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(sub.getEffectiveStatus()).isEqualTo(Subscription.ACTIVE);
    }

    @Test
    void eventForUnknownCustomer_notHandled() {
        when(subscriptions.findByStripeCustomerId("cus_ghost")).thenReturn(Optional.empty());

        assertThat(billing.applyEvent(new PaymentEvent(
            "evt_4", "stripe", "subscription_updated", "cus_ghost", null, "active",
            null, null, null))).isFalse();
        verify(processedWebhookEvents, never()).save(any());
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
    void startPaymentMethodSetup_attachesCustomerId() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(users.findById(USER)).thenReturn(Optional.of(
            com.omyfish.identity.domain.model.User.create(
                "angler@example.com", "hash", "user")));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.createSetupIntent(USER, "angler@example.com"))
            .thenReturn(Optional.of(
                new PaymentPort.SetupIntentResult("stripe", "cus_123", "seti_secret_abc")));

        Optional<PaymentPort.SetupIntentResult> intent = billing.startPaymentMethodSetup(USER);

        assertThat(intent).isPresent();
        assertThat(intent.get().clientSecret()).isEqualTo("seti_secret_abc");
        assertThat(sub.getStripeCustomerId()).isEqualTo("cus_123");
        assertThat(sub.getPaymentProcessor()).isEqualTo("stripe");
    }

    @Test
    void applyEvent_paymentMethodAttached_setsDefaultOnPaymentPort() {
        boolean handled = billing.applyEvent(new PaymentEvent(
            "evt_5", "stripe", "payment_method_attached", "cus_123", null, null,
            null, "pm_456", null));

        assertThat(handled).isTrue();
        verify(payments).setDefaultPaymentMethod("cus_123", "pm_456");
        verify(processedWebhookEvents).save(any());
    }

    @Test
    void applyEvent_dispatchesSetDefaultPaymentMethod_viaEventProcessorNotDefault() {
        when(processors.byName("paypal")).thenReturn(paypalPayments);

        boolean handled = billing.applyEvent(new PaymentEvent(
            "evt_paypal", "paypal", "payment_method_attached", "cus_123", null, null,
            null, "pm_456", null));

        assertThat(handled).isTrue();
        verify(paypalPayments).setDefaultPaymentMethod("cus_123", "pm_456");
        verifyNoInteractions(payments);
    }

    @Test
    void applyEvent_duplicateEventId_skipsReprocessing() {
        when(processedWebhookEvents.existsById("evt_dup")).thenReturn(true);

        boolean handled = billing.applyEvent(new PaymentEvent(
            "evt_dup", "stripe", "payment_method_attached", "cus_123", null, null,
            null, "pm_456", null));

        assertThat(handled).isTrue();
        verifyNoInteractions(payments);
        verify(processedWebhookEvents, never()).save(any());
    }

    @Test
    void refund_delegatesToPaymentPort() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("stripe", "cus_123", "sub_456");
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER))
            .thenReturn(IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(payments.refundSubscription("sub_456", null, 500L, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PaymentPort.RefundResult("re_1", "succeeded", 500L)));

        PaymentPort.RefundResult result = billing.refund(USER, 500L, IDEMPOTENCY_KEY);

        assertThat(result.refundId()).isEqualTo("re_1");
        assertThat(result.amountCents()).isEqualTo(500L);
        verify(idempotencyKeys).save(any());
    }

    @Test
    void refund_resolvesProcessorFromSubscription_notAlwaysDefault() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("paypal", "cus_123", "I-sub-456");
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.empty());
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER))
            .thenReturn(IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER));
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(processors.byName("paypal")).thenReturn(paypalPayments);
        when(paypalPayments.refundSubscription("I-sub-456", null, 500L, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PaymentPort.RefundResult("re_2", "COMPLETED", 500L)));

        PaymentPort.RefundResult result = billing.refund(USER, 500L, IDEMPOTENCY_KEY);

        assertThat(result.refundId()).isEqualTo("re_2");
        verify(processors).byName("paypal");
        verifyNoInteractions(payments);
    }

    @Test
    void refund_noSubscription_throws() {
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.empty());
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billing.refund(USER, null, IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refund_noStripeSubscriptionId_throws() {
        Subscription sub = Subscription.startTrial(USER, 7);
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.empty());
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> billing.refund(USER, null, IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refund_withCompletedIdempotencyKey_replaysStoredResultWithoutCallingStripe() {
        IdempotencyRecord record =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER);
        record.completeRefund("re_1", "succeeded", 500L);
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.of(record));

        PaymentPort.RefundResult result = billing.refund(USER, 500L, IDEMPOTENCY_KEY);

        assertThat(result.refundId()).isEqualTo("re_1");
        verifyNoInteractions(payments);
        verify(subscriptions, never()).findByUserId(any());
    }

    @Test
    void refund_withStaleInProgressIdempotencyKey_retriesAndCompletes() {
        IdempotencyRecord stale = staleRecord(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER);
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.of(stale));
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("stripe", "cus_123", "sub_456");
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(payments.refundSubscription("sub_456", null, 500L, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PaymentPort.RefundResult("re_1", "succeeded", 500L)));

        PaymentPort.RefundResult result = billing.refund(USER, 500L, IDEMPOTENCY_KEY);

        assertThat(result.refundId()).isEqualTo("re_1");
        assertThat(stale.isCompleted()).isTrue();
        verify(idempotencyKeys, never()).reserve(any(), any(), any());
        verify(idempotencyKeys).save(stale);
    }

    @Test
    void refund_whenStripeCallFails_releasesReservationForRetry() {
        Subscription sub = Subscription.startTrial(USER, 7);
        sub.attachProcessor("stripe", "cus_123", "sub_456");
        when(idempotencyKeys.find(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND))
            .thenReturn(Optional.empty());
        IdempotencyRecord reservation =
            IdempotencyRecord.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER);
        when(idempotencyKeys.reserve(IDEMPOTENCY_KEY, IdempotencyRecord.REFUND, USER))
            .thenReturn(reservation);
        when(subscriptions.findByUserId(USER)).thenReturn(Optional.of(sub));
        when(payments.refundSubscription("sub_456", null, null, IDEMPOTENCY_KEY))
            .thenThrow(new IllegalStateException("Stripe refund failed: boom"));

        assertThatThrownBy(() -> billing.refund(USER, null, IDEMPOTENCY_KEY))
            .isInstanceOf(IllegalStateException.class);

        verify(idempotencyKeys).delete(reservation);
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
