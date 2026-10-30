package com.omyfish.shop.adapter.out.payment;

import com.omyfish.shop.domain.port.out.PaymentPort;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stands in for Stripe/PayPal/Adyen under k6 load testing (M7) — calls a WireMock container
 * stubbing a generic payment gateway at a fixed latency instead of a real processor, so load
 * tests never hit real credentials or rate limits. Only exists in the Spring context when
 * explicitly enabled (payment.mock.enabled=true, set by services/shop-service/loadtest's
 * compose override) — never a candidate processor otherwise, so a normal run can't accidentally
 * fall back to it.
 */
@Component
@ConditionalOnProperty(prefix = "payment.mock", name = "enabled", havingValue = "true")
public class MockPaymentCheckoutAdapter implements PaymentPort {

    private final RestClient http;

    public MockPaymentCheckoutAdapter(@Value("${payment.mock.base-url:http://wiremock:8080}") String baseUrl) {
        this.http = RestClient.create(baseUrl);
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    @Retry(name = "mock")
    @CircuitBreaker(name = "mock")
    @Bulkhead(name = "mock")
    public Optional<CheckoutSession> createCheckoutSession(
        UUID orderId, List<LineItem> lines, String successUrl, String cancelUrl, String idempotencyKey,
        String customerEmail
    ) {
        try {
            http.post().uri("/v1/checkout/sessions")
                .header("Idempotency-Key", idempotencyKey)
                .body(Map.of("orderId", orderId.toString()))
                .retrieve()
                .toBodilessEntity();
            String sessionId = "mock_" + idempotencyKey;
            return Optional.of(new CheckoutSession(sessionId, successUrl));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Mock payment gateway call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "mock")
    @CircuitBreaker(name = "mock")
    @Bulkhead(name = "mock")
    public Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey) {
        try {
            http.post().uri("/v1/refunds")
                .header("Idempotency-Key", idempotencyKey)
                .retrieve()
                .toBodilessEntity();
            return Optional.of(new RefundResult("mock_refund_" + idempotencyKey, "succeeded", amountCents));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Mock payment gateway refund failed: " + e.getMessage(), e);
        }
    }

    // The load test exercises the checkout hot path (reserve -> create session), not the
    // asynchronous payment-confirmation webhook — out of scope for M7, see LOAD_TEST_RESULTS.md.
    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers) {
        return Optional.empty();
    }
}
