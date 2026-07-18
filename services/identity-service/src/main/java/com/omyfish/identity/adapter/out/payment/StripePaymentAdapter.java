package com.omyfish.identity.adapter.out.payment;

import com.omyfish.identity.domain.port.out.PaymentPort;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StripePaymentAdapter implements PaymentPort {

    private final String secretKey;
    private final String webhookSecret;
    private final Map<String, String> priceIds;
    private final String appBaseUrl;

    public StripePaymentAdapter(
        @Value("${stripe.secret-key:}") String secretKey,
        @Value("${stripe.webhook-secret:}") String webhookSecret,
        @Value("${stripe.price-monthly:}") String priceMonthly,
        @Value("${stripe.price-yearly:}") String priceYearly,
        @Value("${app.base-url:http://localhost:3000}") String appBaseUrl
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.priceIds = Map.of("monthly", priceMonthly, "yearly", priceYearly);
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    @Override
    public Optional<String> createCheckoutUrl(UUID userId, String email, String plan) {
        String priceId = priceIds.getOrDefault(plan, "");
        if (secretKey.isBlank() || priceId.isBlank()) {
            return Optional.empty();
        }
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(email)
                .setClientReferenceId(userId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(priceId).setQuantity(1L).build())
                .setSuccessUrl(appBaseUrl + "/account?billing=success")
                .setCancelUrl(appBaseUrl + "/account?billing=canceled")
                .putMetadata("user_id", userId.toString())
                .putMetadata("plan", plan)
                .build();
            Session session = Session.create(
                params, RequestOptions.builder().setApiKey(secretKey).build());
            return Optional.of(session.getUrl());
        } catch (Exception e) {
            throw new IllegalStateException("Stripe checkout failed: " + e.getMessage(), e);
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
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                yield session == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    "checkout_completed",
                    session.getClientReferenceId(),
                    session.getMetadata().getOrDefault("plan", "monthly"),
                    session.getCustomer(),
                    session.getSubscription(),
                    null, null));
            }
            case "customer.subscription.updated", "customer.subscription.deleted" -> {
                com.stripe.model.Subscription sub =
                    (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (sub == null) yield Optional.empty();
                Long periodEnd = sub.getItems() != null
                    && !sub.getItems().getData().isEmpty()
                    ? sub.getItems().getData().get(0).getCurrentPeriodEnd() : null;
                yield Optional.of(new PaymentEvent(
                    event.getType().endsWith("deleted")
                        ? "subscription_deleted" : "subscription_updated",
                    null, null,
                    sub.getCustomer(), sub.getId(), sub.getStatus(),
                    periodEnd == null ? null : Instant.ofEpochSecond(periodEnd)));
            }
            default -> Optional.empty();
        };
    }
}
