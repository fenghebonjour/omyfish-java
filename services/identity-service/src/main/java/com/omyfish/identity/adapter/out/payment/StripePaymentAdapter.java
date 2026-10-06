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
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StripePaymentAdapter implements PaymentPort {

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
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    @Override
    public Optional<SubscriptionIntent> createSubscriptionIntent(
        UUID userId, String email, String plan
    ) {
        String priceId = priceIds.getOrDefault(plan, "");
        if (secretKey.isBlank() || priceId.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey).build();
            String customerId = findOrCreateCustomer(userId, email, options);

            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .addAllExpand(List.of("latest_invoice.confirmation_secret"))
                .putMetadata("user_id", userId.toString())
                .putMetadata("plan", plan)
                .build();
            Subscription subscription = Subscription.create(params, options);

            Invoice invoice = subscription.getLatestInvoiceObject();
            String clientSecret = invoice == null || invoice.getConfirmationSecret() == null
                ? null : invoice.getConfirmationSecret().getClientSecret();
            return Optional.of(new SubscriptionIntent(
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
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey).build();
            String customerId = findOrCreateCustomer(userId, email, options);

            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build();
            SetupIntent setupIntent = SetupIntent.create(params, options);

            return Optional.of(new SetupIntentResult(customerId, setupIntent.getClientSecret()));
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
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey).build();
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
    public Optional<RefundResult> refundSubscription(String stripeSubscriptionId, Long amountCents) {
        if (secretKey.isBlank() || stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return Optional.empty();
        }
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey).build();
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
            Refund refund = Refund.create(params.build(), options);
            return Optional.of(new RefundResult(refund.getId(), refund.getStatus(), amountCents));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            return Optional.empty();
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            return Optional.empty();
        }

        return switch (event.getType()) {
            case "customer.subscription.updated", "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                if (sub == null) yield Optional.empty();
                Long periodEnd = sub.getItems() != null
                    && !sub.getItems().getData().isEmpty()
                    ? sub.getItems().getData().get(0).getCurrentPeriodEnd() : null;
                yield Optional.of(new PaymentEvent(
                    event.getType().endsWith("deleted")
                        ? "subscription_deleted" : "subscription_updated",
                    sub.getCustomer(), sub.getId(), sub.getStatus(),
                    periodEnd == null ? null : Instant.ofEpochSecond(periodEnd),
                    null));
            }
            case "setup_intent.succeeded" -> {
                SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                yield setupIntent == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    "payment_method_attached",
                    setupIntent.getCustomer(), null, null, null,
                    setupIntent.getPaymentMethod()));
            }
            default -> Optional.empty();
        };
    }
}
