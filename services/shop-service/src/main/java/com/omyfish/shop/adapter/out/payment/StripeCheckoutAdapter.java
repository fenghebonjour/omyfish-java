package com.omyfish.shop.adapter.out.payment;

import com.omyfish.shared.stripe.StripeHttpSupport;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shop.domain.port.out.PaymentPort;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StripeCheckoutAdapter implements PaymentPort {

    private final String secretKey;
    private final String webhookSecret;
    private final String currency;
    private final StripeHttpSupport stripe;

    public StripeCheckoutAdapter(
        @Value("${stripe.secret-key:}") String secretKey,
        @Value("${stripe.shop-webhook-secret:}") String webhookSecret,
        @Value("${stripe.currency:cad}") String currency
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.currency = currency;
        this.stripe = new StripeHttpSupport(secretKey);
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
        return stripe.baseOptions();
    }

    @Override
    public Optional<CheckoutSession> createCheckoutSession(
        UUID orderId, List<LineItem> lines, String successUrl, String cancelUrl, String idempotencyKey,
        String customerEmail
    ) {
        if (secretKey.isBlank()) {
            return Optional.empty();
        }
        try {
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("order_id", orderId.toString())
                // Lures/bait are physical goods — every checkout must collect somewhere to ship
                // them. Canada-only for now, matching the rest of the app's Quebec/CAD focus.
                .setShippingAddressCollection(SessionCreateParams.ShippingAddressCollection.builder()
                    .addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.CA)
                    .build());

            if (customerEmail != null && !customerEmail.isBlank()) {
                // Only set for guest checkout — an account checkout has no reliable email handy
                // at this layer, and Stripe's own page still asks for one either way.
                params.setCustomerEmail(customerEmail);
            }

            for (LineItem line : lines) {
                params.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) line.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(line.unitPriceCents())
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(line.name())
                            .build())
                        .build())
                    .build());
            }

            RequestOptions options = baseOptions().setIdempotencyKey(idempotencyKey).build();
            Session session = Session.create(params.build(), options);
            return Optional.of(new CheckoutSession(session.getId(), session.getUrl()));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe checkout session creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey) {
        if (secretKey.isBlank() || paymentIntentId == null || paymentIntentId.isBlank()) {
            return Optional.empty();
        }
        try {
            RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId);
            if (amountCents != null) {
                params.setAmount(amountCents);
            }
            RequestOptions options = baseOptions().setIdempotencyKey(idempotencyKey).build();
            Refund refund = Refund.create(params.build(), options);
            return Optional.of(new RefundResult(refund.getId(), refund.getStatus(), amountCents));
        } catch (Exception e) {
            throw new IllegalStateException("Stripe refund failed: " + e.getMessage(), e);
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
            case "checkout.session.completed" -> {
                Session session = deserializeEventObject(event, Session.class);
                yield session == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    PaymentEventType.SESSION_COMPLETED, session.getId(), session.getPaymentIntent(),
                    extractShippingAddress(session)));
            }
            case "checkout.session.expired" -> {
                Session session = deserializeEventObject(event, Session.class);
                yield session == null ? Optional.empty() : Optional.of(new PaymentEvent(
                    PaymentEventType.SESSION_EXPIRED, session.getId(), session.getPaymentIntent(), null));
            }
            default -> Optional.empty();
        };
    }

    // Null whenever the buyer hasn't actually filled in the shipping step yet — shouldn't
    // happen for a genuinely completed session (ShippingAddressCollection is always set on
    // creation), but a missing address shouldn't crash the webhook if it ever does.
    private ShippingAddress extractShippingAddress(Session session) {
        var collected = session.getCollectedInformation();
        if (collected == null || collected.getShippingDetails() == null) {
            return null;
        }
        var details = collected.getShippingDetails();
        var address = details.getAddress();
        if (address == null) {
            return null;
        }
        return new ShippingAddress(
            details.getName(), address.getLine1(), address.getLine2(), address.getCity(),
            address.getState(), address.getPostalCode(), address.getCountry());
    }

    // See StripeHttpSupport.deserializeEventObject (BACKLOG item M) for why this fallback exists.
    private <T extends StripeObject> T deserializeEventObject(Event event, Class<T> type) {
        return stripe.deserializeEventObject(event, type);
    }
}
