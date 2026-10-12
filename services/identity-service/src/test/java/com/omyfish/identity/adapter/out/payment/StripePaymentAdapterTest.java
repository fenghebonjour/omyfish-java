package com.omyfish.identity.adapter.out.payment;

import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StripePaymentAdapterTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final StripePaymentAdapter adapter =
        new StripePaymentAdapter("sk_test", WEBHOOK_SECRET, "price_monthly", "price_yearly");

    // BACKLOG item L: getDataObjectDeserializer().getObject() comes back empty whenever the
    // event's api_version doesn't match the stripe-java SDK's compiled version — common in a
    // sandbox whose account api_version trails the library. verifyWebhook() must still recover
    // the subscription via deserializeUnsafe() instead of silently dropping the event.
    @Test
    void verifyWebhook_subscriptionUpdated_withMismatchedApiVersion_stillMapsEvent() {
        String payload = """
            {
              "id": "evt_1",
              "object": "event",
              "api_version": "2019-02-19",
              "type": "customer.subscription.updated",
              "data": {
                "object": {
                  "id": "sub_456",
                  "object": "subscription",
                  "customer": "cus_123",
                  "status": "active",
                  "cancel_at_period_end": true
                }
              }
            }
            """;

        Optional<PaymentEvent> result = adapter.verifyWebhook(payload, signedHeaders(payload));

        assertThat(result).isPresent();
        PaymentEvent event = result.get();
        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.type()).isEqualTo("subscription_updated");
        assertThat(event.customerId()).isEqualTo("cus_123");
        assertThat(event.subscriptionId()).isEqualTo("sub_456");
        assertThat(event.providerStatus()).isEqualTo("active");
        assertThat(event.cancelAtPeriodEnd()).isTrue();
    }

    private Map<String, String> signedHeaders(String payload) {
        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            String signedPayload = timestamp + "." + payload;
            String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signedPayload);
            return Map.of("stripe-signature", "t=" + timestamp + ",v1=" + signature);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
