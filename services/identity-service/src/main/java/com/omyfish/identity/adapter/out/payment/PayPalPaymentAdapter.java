package com.omyfish.identity.adapter.out.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.identity.domain.port.out.PaymentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Direct PayPal integration (Subscriptions + Vault APIs) — not routed through Stripe. A sibling
 * acquirer to StripePaymentAdapter behind the same PaymentPort, not a Stripe payment method.
 *
 * Model differences from Stripe, by necessity rather than by choice:
 *  - No client-secret concept: SubscriptionIntent/SetupIntentResult's clientSecret slot instead
 *    carries the "approve" link the frontend must redirect the shopper to.
 *  - customerId is unknown at subscription-creation time (PayPal only assigns a payer_id once
 *    the shopper approves) — it starts empty and is filled in by the ACTIVATED webhook, the
 *    same way Stripe's own webhook refines state after creation.
 *  - setDefaultPaymentMethod is a no-op: PayPal's Vault API has no customer-level "default
 *    payment method" pointer the way Stripe does; the payment-token id is referenced per-charge.
 */
@Component
public class PayPalPaymentAdapter implements PaymentPort {

    private final String clientId;
    private final String clientSecret;
    private final String webhookId;
    private final String returnUrl;
    private final String cancelUrl;
    private final Map<String, String> planIds;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    public PayPalPaymentAdapter(
        @Value("${paypal.client-id:}") String clientId,
        @Value("${paypal.client-secret:}") String clientSecret,
        @Value("${paypal.webhook-id:}") String webhookId,
        @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
        @Value("${paypal.plan-monthly:}") String planMonthly,
        @Value("${paypal.plan-yearly:}") String planYearly,
        @Value("${paypal.return-url:}") String returnUrl,
        @Value("${paypal.cancel-url:}") String cancelUrl,
        ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webhookId = webhookId;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.planIds = Map.of("monthly", planMonthly, "yearly", planYearly);
        this.objectMapper = objectMapper;

        // Same timeout discipline as AIServiceAdapter's WebClient — a slow (not down) PayPal
        // must not hang a request indefinitely.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }

    @Override
    public String name() {
        return "paypal";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private synchronized String accessToken() {
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        TokenResponse response = restClient.post()
            .uri("/v1/oauth2/token")
            .headers(h -> h.setBasicAuth(clientId, clientSecret))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials")
            .retrieve()
            .body(TokenResponse.class);
        if (response == null) {
            throw new IllegalStateException("PayPal token request returned no body");
        }
        cachedToken = response.access_token();
        tokenExpiry = Instant.now().plusSeconds(Math.max(0, response.expires_in() - 60));
        return cachedToken;
    }

    @Override
    public Optional<SubscriptionIntent> createSubscriptionIntent(
        UUID userId, String email, String plan, String idempotencyKey
    ) {
        String planId = planIds.getOrDefault(plan, "");
        if (!isConfigured() || planId.isBlank() || returnUrl.isBlank() || cancelUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            SubscriptionCreateRequest body = new SubscriptionCreateRequest(
                planId,
                new Subscriber(email),
                new ApplicationContext(returnUrl, cancelUrl));
            SubscriptionCreateResponse response = restClient.post()
                .uri("/v1/billing/subscriptions")
                .headers(h -> {
                    h.setBearerAuth(accessToken());
                    h.set("PayPal-Request-Id", idempotencyKey);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(SubscriptionCreateResponse.class);
            if (response == null) return Optional.empty();
            String approveUrl = approveLink(response.links());
            // customerId left blank: PayPal only assigns a payer_id once the shopper approves;
            // the ACTIVATED webhook fills it in.
            return Optional.of(new SubscriptionIntent(
                name(), "", response.id(), approveUrl, response.status()));
        } catch (Exception e) {
            throw new IllegalStateException("PayPal checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SetupIntentResult> createSetupIntent(UUID userId, String email) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            // Creates a Vault setup token for the PayPal wallet; the frontend's PayPal JS SDK
            // completes it (exchanges it for a reusable payment token) after the shopper
            // approves, the same way Stripe.js completes a SetupIntent client-side.
            SetupTokenRequest body = new SetupTokenRequest(new PaymentSource(
                new PayPalWalletSource("IMMEDIATE", "MERCHANT", "CONSUMER", false,
                    new ApplicationContext(returnUrl, cancelUrl))));
            SetupTokenResponse response = restClient.post()
                .uri("/v3/vault/setup-tokens")
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(SetupTokenResponse.class);
            if (response == null) return Optional.empty();
            return Optional.of(new SetupIntentResult(name(), "", approveLink(response.links())));
        } catch (Exception e) {
            throw new IllegalStateException("PayPal setup token failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setDefaultPaymentMethod(String customerId, String paymentMethodId) {
        // No-op: PayPal's Vault API has no customer-level "default payment method" the way
        // Stripe does — the payment-token id is referenced directly on each future charge.
    }

    @Override
    public Optional<RefundResult> refundSubscription(
        String subscriptionId, String lastPaymentReference, Long amountCents, String idempotencyKey
    ) {
        if (!isConfigured() || subscriptionId == null || subscriptionId.isBlank()) {
            return Optional.empty();
        }
        try {
            Instant now = Instant.now();
            TransactionsResponse transactions = restClient.get()
                .uri(b -> b.path("/v1/billing/subscriptions/{id}/transactions")
                    .queryParam("start_time", now.minusSeconds(366L * 86_400).toString())
                    .queryParam("end_time", now.toString())
                    .build(subscriptionId))
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .body(TransactionsResponse.class);
            if (transactions == null || transactions.transactions() == null) return Optional.empty();
            // The latest COMPLETED transaction's id doubles as the capture id for refund
            // purposes, per PayPal's Subscriptions transactions endpoint.
            String captureId = transactions.transactions().stream()
                .filter(t -> "COMPLETED".equals(t.status()))
                .reduce((first, second) -> second)
                .map(Transaction::id)
                .orElse(null);
            if (captureId == null) return Optional.empty();

            RefundCreateRequest body = amountCents == null
                ? new RefundCreateRequest(null)
                : new RefundCreateRequest(new Amount(centsToAmount(amountCents), "USD"));
            RefundCreateResponse response = restClient.post()
                .uri("/v2/payments/captures/{id}/refund", captureId)
                .headers(h -> {
                    h.setBearerAuth(accessToken());
                    h.set("PayPal-Request-Id", idempotencyKey);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RefundCreateResponse.class);
            if (response == null) return Optional.empty();
            return Optional.of(new RefundResult(response.id(), response.status(), amountCents));
        } catch (Exception e) {
            throw new IllegalStateException("PayPal refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers) {
        if (!isConfigured() || webhookId.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            return Optional.empty();
        }
        try {
            VerifyWebhookResponse verification = restClient.post()
                .uri("/v1/notifications/verify-webhook-signature")
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyWebhookRequest(
                    headers.get("paypal-auth-algo"),
                    headers.get("paypal-cert-url"),
                    headers.get("paypal-transmission-id"),
                    headers.get("paypal-transmission-sig"),
                    headers.get("paypal-transmission-time"),
                    webhookId,
                    root))
                .retrieve()
                .body(VerifyWebhookResponse.class);
            if (verification == null || !"SUCCESS".equals(verification.verification_status())) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }

        String eventId = textOrNull(root.path("id"));
        String eventType = textOrNull(root.path("event_type"));
        JsonNode resource = root.path("resource");

        return switch (eventType == null ? "" : eventType) {
            case "BILLING.SUBSCRIPTION.ACTIVATED", "BILLING.SUBSCRIPTION.UPDATED" -> Optional.of(new PaymentEvent(
                eventId, name(), "subscription_updated",
                textOrNull(resource.path("subscriber").path("payer_id")),
                textOrNull(resource.path("id")),
                textOrNull(resource.path("status")),
                parseInstant(textOrNull(resource.path("billing_info").path("next_billing_time"))),
                null, null));
            case "BILLING.SUBSCRIPTION.CANCELLED", "BILLING.SUBSCRIPTION.EXPIRED",
                "BILLING.SUBSCRIPTION.SUSPENDED" -> Optional.of(new PaymentEvent(
                eventId, name(), "subscription_deleted",
                textOrNull(resource.path("subscriber").path("payer_id")),
                textOrNull(resource.path("id")),
                null, null, null, null));
            case "VAULT.PAYMENT-TOKEN.CREATED" -> Optional.of(new PaymentEvent(
                eventId, name(), "payment_method_attached",
                textOrNull(resource.path("customer").path("id")),
                null, null, null,
                textOrNull(resource.path("id")), null));
            default -> Optional.empty();
        };
    }

    private static String approveLink(List<Link> links) {
        if (links == null) return null;
        return links.stream()
            .filter(l -> "approve".equals(l.rel()))
            .map(Link::href)
            .findFirst().orElse(null);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    private static String centsToAmount(long amountCents) {
        return String.format("%d.%02d", amountCents / 100, amountCents % 100);
    }

    private record TokenResponse(String access_token, String token_type, long expires_in) {}

    private record Subscriber(String email_address) {}
    private record ApplicationContext(String return_url, String cancel_url) {}
    private record SubscriptionCreateRequest(
        String plan_id, Subscriber subscriber, ApplicationContext application_context) {}
    private record Link(String rel, String href) {}
    private record SubscriptionCreateResponse(String id, String status, List<Link> links) {}

    private record PayPalWalletSource(
        String usage_pattern, String usage_type, String customer_type,
        boolean permit_multiple_payment_tokens, ApplicationContext experience_context) {}
    private record PaymentSource(PayPalWalletSource paypal) {}
    private record SetupTokenRequest(PaymentSource payment_source) {}
    private record SetupTokenResponse(String id, String status, List<Link> links) {}

    private record Transaction(String id, String status) {}
    private record TransactionsResponse(List<Transaction> transactions) {}
    private record Amount(String value, String currency_code) {}
    private record RefundCreateRequest(Amount amount) {}
    private record RefundCreateResponse(String id, String status) {}

    private record VerifyWebhookRequest(
        String auth_algo, String cert_url, String transmission_id,
        String transmission_sig, String transmission_time, String webhook_id,
        JsonNode webhook_event) {}
    private record VerifyWebhookResponse(String verification_status) {}
}
