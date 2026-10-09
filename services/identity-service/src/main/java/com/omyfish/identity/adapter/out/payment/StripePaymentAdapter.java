package com.omyfish.identity.adapter.out.payment;

import com.omyfish.identity.domain.port.out.PaymentPort;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoicePayment;
import com.stripe.model.Refund;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.billingportal.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StripePaymentAdapter implements PaymentPort {

    // Same timeout discipline as AIServiceAdapter's WebClient fix (WEAKNESS_AUDIT.md §2.1) — a
    // slow (not down) Stripe must not hang a request indefinitely. No retries added: a failed
    // charge/subscription-create must never be retried without an idempotency key in place
    // (BACKLOG item I.1, already done), so retrying here would risk a double-charge.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final String secretKey;
    private final String webhookSecret;
    private final Map<String, String> priceIds;

    public StripePaymentAdapter(
        @Value("${stripe.secret-key:}") String secretKey,
        @Value("${stripe.webhook-secret:}") String webhookSecret,
        @Value("${stripe.price-monthly:}") String priceMonthly,
        @Value("${stripe.price-yearly:}") String priceYearly
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.priceIds = Map.of("monthly", priceMonthly, "yearly", priceYearly);
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    private RequestOptions.RequestOptionsBuilder baseOptions() {
        return RequestOptions.builder()
            .setApiKey(secretKey)
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setReadTimeout(READ_TIMEOUT_MS);
    }

    @Override
    public Optional<SubscriptionIntent> createSubscriptionIntent(
        UUID userId, String email, String plan, String idempotencyKey
    ) {
        String priceId = priceIds.getOrDefault(plan, "");
        if (secretKey.isBlank() || priceId.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = baseOptions().build();
            String customerId = findOrCreateCustomer(userId, email, options);

            RequestOptions createOptions = baseOptions()
                .setIdempotencyKey(idempotencyKey)
                .build();
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .addAllExpand(List.of("latest_invoice.confirmation_secret"))
                .putMetadata("user_id", userId.toString())
                .putMetadata("plan", plan)
                .build();
            Subscription subscription = Subscription.create(params, createOptions);

            Invoice invoice = subscription.getLatestInvoiceObject();
            String clientSecret = invoice == null || invoice.getConfirmationSecret() == null
                ? null : invoice.getConfirmationSecret().getClientSecret();
            return Optional.of(new SubscriptionIntent(
                name(),
                subscription.getCustomer(),
                subscription.getId(),
                clientSecret,
                subscription.getStatus()));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SetupIntentResult> createSetupIntent(UUID userId, String email) {
        if (secretKey.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = baseOptions().build();
            String customerId = findOrCreateCustomer(userId, email, options);

            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build();
            SetupIntent setupIntent = SetupIntent.create(params, options);

            return Optional.of(new SetupIntentResult(name(), customerId, setupIntent.getClientSecret()));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe setup intent failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setDefaultPaymentMethod(String customerId, String paymentMethodId) {
        if (secretKey.isBlank()) {
            return;
        }
        try {
            RequestOptions options = baseOptions().build();
            Customer.retrieve(customerId, options).update(
                CustomerUpdateParams.builder()
                    .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build())
                    .build(),
                options);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Stripe default payment method update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> createPortalSession(String customerId, String returnUrl) {
        if (secretKey.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = baseOptions().build();
            SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build();
            Session session = Session.create(params, options);
            return Optional.of(session.getUrl());
        } catch (Exception e) {
            throw new IllegalStateException("Stripe portal session failed: " + e.getMessage(), e);
        }
    }

    /** Reverses {@link #priceIds} to recover our plan name from a Stripe price id on a webhook event. */
    private String planForPriceId(String priceId) {
        if (priceId == null) return null;
        return priceIds.entrySet().stream()
            .filter(e -> e.getValue().equals(priceId))
            .map(Map.Entry::getKey)
            .findFirst().orElse(null);
    }

    private String findOrCreateCustomer(UUID userId, String email, RequestOptions options)
        throws Exception {
        Customer existing = Customer.list(
            CustomerListParams.builder().setEmail(email).setLimit(1L).build(), options)
            .getData().stream().findFirst().orElse(null);
        if (existing != null) {
            return existing.getId();
        }
        Customer created = Customer.create(
            CustomerCreateParams.builder()
                .setEmail(email)
                .putMetadata("user_id", userId.toString())
                .build(),
            options);
        return created.getId();
    }

    @Override
    public Optional<RefundResult> refundSubscription(
        String stripeSubscriptionId, String lastPaymentReference, Long amountCents, String idempotencyKey
    ) {
        // lastPaymentReference unused: Stripe's own subscription object already tells us the
        // latest invoice/payment, so there's nothing to look up from outside.
        if (secretKey.isBlank() || stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = baseOptions().build();
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId, options);
            String invoiceId = subscription.getLatestInvoice();
            if (invoiceId == null) {
                return Optional.empty();
            }

            InvoicePayment payment = InvoicePayment.list(
                InvoicePaymentListParams.builder()
                    .setInvoice(invoiceId)
                    .setStatus(InvoicePaymentListParams.Status.PAID)
                    .setLimit(1L)
                    .build(),
                options)
                .getData().stream().findFirst().orElse(null);
            String paymentIntentId = payment == null ? null : payment.getPayment().getPaymentIntent();
            if (paymentIntentId == null) {
                return Optional.empty();
            }

            RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId);
            if (amountCents != null) {
                params.setAmount(amountCents);
            }
            RequestOptions refundOptions = baseOptions()
                .setIdempotencyKey(idempotencyKey)
                .build();
            Refund refund = Refund.create(params.build(), refundOptions);
            return Optional.of(new RefundResult(refund.getId(), refund.getStatus(), amountCents));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ReconciliationCandidate> listRecentSubscriptions(Instant since) {
        if (secretKey.isBlank()) {
            return List.of();
        }
        try {
            RequestOptions options = baseOptions().build();
            SubscriptionListParams params = SubscriptionListParams.builder()
                .setCreated(SubscriptionListParams.Created.builder()
                    .setGte(since.getEpochSecond())
                    .build())
                .setLimit(100L)
                .build();
            List<ReconciliationCandidate> candidates = new ArrayList<>();
            for (Subscription sub : Subscription.list(params, options).autoPagingIterable()) {
                Map<String, String> metadata = sub.getMetadata();
                String userIdStr = metadata == null ? null : metadata.get("user_id");
                UUID userId;
                try {
                    userId = userIdStr == null ? null : UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    userId = null;
                }
                Long periodEndEpoch = sub.getItems() != null && !sub.getItems().getData().isEmpty()
                    ? sub.getItems().getData().get(0).getCurrentPeriodEnd() : null;
                candidates.add(new ReconciliationCandidate(
                    name(), userId, sub.getCustomer(), sub.getId(),
                    metadata == null ? null : metadata.get("plan"), sub.getStatus(),
                    periodEndEpoch == null ? null : Instant.ofEpochSecond(periodEndEpoch)));
            }
            return candidates;
        } catch (Exception e) {
            throw new IllegalStateException("Stripe subscription listing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers) {
        if (webhookSecret.isBlank()) {
            return Optional.empty();
        }
        String signature = headers.get("stripe-signature");
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature == null ? "" : signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            return Optional.empty();
        }

        return switch (event.getType()) {
            case "customer.subscription.updated", "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                if (sub == null) yield Optional.empty();
                boolean hasItems = sub.getItems() != null && !sub.getItems().getData().isEmpty();
                Long periodEnd = hasItems ? sub.getItems().getData().get(0).getCurrentPeriodEnd() : null;
                String priceId = hasItems ? sub.getItems().getData().get(0).getPrice().getId() : null;
                yield Optional.of(new PaymentEvent(
                    event.getId(), name(),
                    event.getType().endsWith("deleted")
                        ? "subscription_deleted" : "subscription_updated",
                    sub.getCustomer(), sub.getId(), sub.getStatus(),
                    periodEnd == null ? null : Instant.ofEpochSecond(periodEnd),
                    null, null, planForPriceId(priceId)));
            }
            case "setup_intent.succeeded" -> {
                SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                yield setupIntent == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    event.getId(), name(),
                    "payment_method_attached",
                    setupIntent.getCustomer(), null, null, null,
                    setupIntent.getPaymentMethod(), null, null));
            }
            case "invoice.payment_failed" -> {
                Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                String subscriptionId = invoice == null || invoice.getParent() == null
                    || invoice.getParent().getSubscriptionDetails() == null ? null
                    : invoice.getParent().getSubscriptionDetails().getSubscription();
                yield subscriptionId == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    event.getId(), name(),
                    "payment_failed",
                    invoice.getCustomer(), subscriptionId, null, null,
                    null, null, null));
            }
            default -> Optional.empty();
        };
    }
}
