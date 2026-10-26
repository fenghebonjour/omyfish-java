package com.omyfish.shared.stripe;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripeHttpSupportTest {

    private final StripeHttpSupport stripe = new StripeHttpSupport("sk_test");

    @Test
    void baseOptions_setsApiKeyAndTimeouts() {
        var options = stripe.baseOptions().build();

        assertThat(options.getApiKey()).isEqualTo("sk_test");
        assertThat(options.getConnectTimeout()).isEqualTo(10_000);
        assertThat(options.getReadTimeout()).isEqualTo(15_000);
    }

    // BACKLOG item L: getDataObjectDeserializer().getObject() comes back empty whenever the
    // event's api_version doesn't match the stripe-java SDK's compiled version — common in a
    // sandbox whose account api_version trails the library. deserializeEventObject() must still
    // recover the object via deserializeUnsafe() instead of returning null.
    @Test
    void deserializeEventObject_withMismatchedApiVersion_fallsBackToDeserializeUnsafe() {
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
                  "status": "active"
                }
              }
            }
            """;
        // Same deserialization path Webhook.constructEvent uses internally, minus the signature
        // check — this test is isolating deserializeEventObject(), not signature verification.
        Event event = ApiResource.GSON.fromJson(payload, Event.class);

        Subscription sub = stripe.deserializeEventObject(event, Subscription.class);

        assertThat(sub).isNotNull();
        assertThat(sub.getId()).isEqualTo("sub_456");
        assertThat(sub.getCustomer()).isEqualTo("cus_123");
    }

    @Test
    void deserializeEventObject_matchingApiVersion_usesGetObjectDirectly() {
        String payload = """
            {
              "id": "evt_2",
              "object": "event",
              "api_version": "%s",
              "type": "customer.subscription.updated",
              "data": {
                "object": {
                  "id": "sub_789",
                  "object": "subscription",
                  "customer": "cus_456",
                  "status": "active"
                }
              }
            }
            """.formatted(com.stripe.Stripe.API_VERSION);
        Event event = ApiResource.GSON.fromJson(payload, Event.class);

        Subscription sub = stripe.deserializeEventObject(event, Subscription.class);

        assertThat(sub).isNotNull();
        assertThat(sub.getId()).isEqualTo("sub_789");
    }
}
