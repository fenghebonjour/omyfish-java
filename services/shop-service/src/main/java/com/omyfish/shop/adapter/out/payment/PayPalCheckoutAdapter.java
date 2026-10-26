package com.omyfish.shop.adapter.out.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shop.domain.port.out.PaymentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Direct PayPal integration (Orders v2 API) for one-shot cart checkout — a sibling acquirer to
 * StripeCheckoutAdapter behind the same (shop-service) PaymentPort, not identity-service's
 * subscription-shaped PayPalPaymentAdapter (different API family: Orders, not Subscriptions).
 * Reuses that adapter's OAuth-token-caching and webhook-signature-verification logic verbatim.
 *
 * Model differences from Stripe, by necessity:
 *  - checkoutUrl is PayPal's "approve" link, same idea as Stripe's hosted Checkout URL.
 *  - intent=CAPTURE never auto-captures — PayPal requires an explicit capture call after the
 *    buyer approves. This app's checkout is entirely webhook-driven (no backend "return"
 *    endpoint the browser hits), so capture happens *inside* verifyWebhook on
 *    CHECKOUT.ORDER.APPROVED; the capture response becomes the SESSION_COMPLETED event. The
 *    later PAYMENT.CAPTURE.COMPLETED webhook is redundant and ignored.
 *  - A redelivered CHECKOUT.ORDER.APPROVED after a capture already succeeded (e.g. our process
 *    crashed before the order got marked PAID locally) hits PayPal's ORDER_ALREADY_CAPTURED
 *    error on a second capture attempt — handled by falling back to GET-ing the order's
 *    existing capture instead of failing the webhook.
 *  - Neither this nor Adyen's Sessions API has an "abandoned checkout" webhook the way Stripe's
 *    checkout.session.expired is — PayPal Orders just stop being approvable after 3 hours with
 *    no event at all. Restocking an abandoned PayPal/Adyen order relies entirely on
 *    StaleOrderSweepJob, not a webhook. No further reconciliation (unlike identity-service's
 *    ReconciliationService for subscriptions) is built here — same documented-gap style as that
 *    module's PayPal/Adyen limitations.
 */
@Component
public class PayPalCheckoutAdapter implements PaymentPort {

    private final String clientId;
    private final String clientSecret;
    private final String webhookId;
    private final String currency;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    public PayPalCheckoutAdapter(
        @Value("${paypal.client-id:}") String clientId,
        @Value("${paypal.client-secret:}") String clientSecret,
        @Value("${paypal.shop-webhook-id:}") String webhookId,
        @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
        @Value("${stripe.currency:cad}") String currency,
        ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webhookId = webhookId;
        this.currency = currency.toUpperCase();
        this.objectMapper = objectMapper;

        // Same timeout discipline as StripeCheckoutAdapter / identity-service's PayPalPaymentAdapter.
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
    public Optional<CheckoutSession> createCheckoutSession(
        UUID orderId, List<LineItem> lines, String successUrl, String cancelUrl, String idempotencyKey,
        String customerEmail
    ) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            List<OrderItem> items = lines.stream()
                .map(l -> new OrderItem(l.name(), String.valueOf(l.quantity()),
                    new Amount(currency, centsToAmount(l.unitPriceCents()))))
                .toList();
            long totalCents = lines.stream().mapToLong(l -> l.unitPriceCents() * l.quantity()).sum();

            PurchaseUnit unit = new PurchaseUnit(
                orderId.toString(),
                new PurchaseUnitAmount(currency, centsToAmount(totalCents), new AmountBreakdown(
                    new Amount(currency, centsToAmount(totalCents)))),
                items,
                (customerEmail != null && !customerEmail.isBlank()) ? new Payer(customerEmail) : null);

            OrderCreateRequest body = new OrderCreateRequest(
                "CAPTURE", List.of(unit), new ApplicationContext(successUrl, cancelUrl));

            OrderCreateResponse response = restClient.post()
                .uri("/v2/checkout/orders")
                .headers(h -> {
                    h.setBearerAuth(accessToken());
                    h.set("PayPal-Request-Id", idempotencyKey);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OrderCreateResponse.class);
            if (response == null) return Optional.empty();

            String approveUrl = approveLink(response.links());
            if (approveUrl == null) return Optional.empty();
            return Optional.of(new CheckoutSession(response.id(), approveUrl));
        } catch (Exception e) {
            throw new IllegalStateException("PayPal checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey) {
        if (!isConfigured() || paymentIntentId == null || paymentIntentId.isBlank()) {
            return Optional.empty();
        }
        try {
            RefundCreateRequest body = amountCents == null
                ? new RefundCreateRequest(null)
                : new RefundCreateRequest(new Amount(currency, centsToAmount(amountCents)));
            RefundCreateResponse response = restClient.post()
                .uri("/v2/payments/captures/{id}/refund", paymentIntentId)
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

        String eventType = textOrNull(root.path("event_type"));
        JsonNode resource = root.path("resource");

        if (!"CHECKOUT.ORDER.APPROVED".equals(eventType)) {
            // PAYMENT.CAPTURE.COMPLETED (fired right after our own capture call below) and
            // everything else: nothing left to do, the APPROVED branch already drove completion.
            return Optional.empty();
        }

        String orderId = textOrNull(resource.path("id"));
        if (orderId == null) return Optional.empty();
        ShippingAddress shippingAddress = extractShippingAddress(resource);

        JsonNode captured = captureOrRecoverCapture(orderId, "capture:" + orderId);
        String captureId = textOrNull(at(at(captured.path("purchase_units"), 0)
            .path("payments").path("captures"), 0).path("id"));
        if (captureId == null) return Optional.empty();

        return Optional.of(new PaymentEvent(PaymentEventType.SESSION_COMPLETED, orderId, captureId, shippingAddress));
    }

    private JsonNode captureOrRecoverCapture(String orderId, String idempotencyKey) {
        try {
            String body = restClient.post()
                .uri("/v2/checkout/orders/{id}/capture", orderId)
                .headers(h -> {
                    h.setBearerAuth(accessToken());
                    h.set("PayPal-Request-Id", idempotencyKey);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(body);
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED")) {
                // A redelivered CHECKOUT.ORDER.APPROVED after a prior capture already
                // succeeded — recover the existing capture instead of failing the webhook.
                String body = restClient.get()
                    .uri("/v2/checkout/orders/{id}", orderId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(String.class);
                try {
                    return objectMapper.readTree(body);
                } catch (Exception parseEx) {
                    throw new IllegalStateException("PayPal order lookup failed: " + parseEx.getMessage(), parseEx);
                }
            }
            throw new IllegalStateException("PayPal capture failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("PayPal capture failed: " + e.getMessage(), e);
        }
    }

    private ShippingAddress extractShippingAddress(JsonNode orderResource) {
        JsonNode shipping = at(orderResource.path("purchase_units"), 0).path("shipping");
        JsonNode address = shipping.path("address");
        if (address.isMissingNode()) return null;
        return new ShippingAddress(
            textOrNull(shipping.path("name").path("full_name")),
            textOrNull(address.path("address_line_1")),
            textOrNull(address.path("address_line_2")),
            textOrNull(address.path("admin_area_2")),
            textOrNull(address.path("admin_area_1")),
            textOrNull(address.path("postal_code")),
            textOrNull(address.path("country_code")));
    }

    private static String approveLink(List<Link> links) {
        if (links == null) return null;
        return links.stream()
            .filter(l -> "approve".equals(l.rel()))
            .map(Link::href)
            .findFirst().orElse(null);
    }

    private static JsonNode at(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || index >= arrayNode.size()) {
            return MissingNode.getInstance();
        }
        return arrayNode.get(index);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static String centsToAmount(long amountCents) {
        return String.format("%d.%02d", amountCents / 100, amountCents % 100);
    }

    private record TokenResponse(String access_token, String token_type, long expires_in) {}

    private record Amount(String currency_code, String value) {}
    private record AmountBreakdown(Amount item_total) {}
    private record PurchaseUnitAmount(String currency_code, String value, AmountBreakdown breakdown) {}
    private record OrderItem(String name, String quantity, Amount unit_amount) {}
    private record Payer(String email_address) {}
    private record PurchaseUnit(String reference_id, PurchaseUnitAmount amount, List<OrderItem> items, Payer payer) {}
    private record ApplicationContext(String return_url, String cancel_url) {}
    private record OrderCreateRequest(String intent, List<PurchaseUnit> purchase_units, ApplicationContext application_context) {}
    private record Link(String rel, String href) {}
    private record OrderCreateResponse(String id, String status, List<Link> links) {}

    private record RefundCreateRequest(Amount amount) {}
    private record RefundCreateResponse(String id, String status) {}

    private record VerifyWebhookRequest(
        String auth_algo, String cert_url, String transmission_id,
        String transmission_sig, String transmission_time, String webhook_id,
        JsonNode webhook_event) {}
    private record VerifyWebhookResponse(String verification_status) {}
}
