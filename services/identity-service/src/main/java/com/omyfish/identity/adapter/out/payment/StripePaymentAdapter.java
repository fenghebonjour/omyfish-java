package com.omyfish.identity.adapter.out.payment;

import com.omyfish.identity.domain.exception.WebhookVerificationException;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StripePaymentAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentAdapter.class);

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
            log.warn("Stripe checkout unavailable: secretKeyConfigured={} priceIdConfigured={} plan={}",
                !secretKey.isBlank(), !priceId.isBlank(), plan);
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
            log.error("Stripe checkout session creation failed for user={} plan={}", userId, plan, e);
            throw new IllegalStateException("Stripe checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            log.error("Received a Stripe webhook but stripe.webhook-secret is not configured");
            throw new IllegalStateException(
                "Cannot verify Stripe webhook: stripe.webhook-secret is not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with an invalid signature: {}", e.getMessage());
            throw new WebhookVerificationException("Invalid Stripe webhook signature", e);
        }

        return switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                if (session == null) {
                    throw new WebhookVerificationException(
                        "Stripe event " + event.getId() + " (" + event.getType()
                            + ") payload could not be deserialized");
                }
                yield Optional.of(new PaymentEvent(
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
                if (sub == null) {
                    throw new WebhookVerificationException(
                        "Stripe event " + event.getId() + " (" + event.getType()
                            + ") payload could not be deserialized");
                }
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
            default -> {
                log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
                yield Optional.empty();
            }
        };
    }
}
