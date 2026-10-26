package com.omyfish.shop.adapter.out.payment;

import com.adyen.Client;
import com.adyen.enums.Environment;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentRefundRequest;
import com.adyen.model.checkout.PaymentRefundResponse;
import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.util.HMACValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.shop.domain.port.out.PaymentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adyen Checkout Sessions for one-shot cart checkout — a sibling acquirer to
 * StripeCheckoutAdapter behind the same (shop-service) PaymentPort, not identity-service's
 * subscription-shaped AdyenPaymentAdapter. Same official SDK usage, just without the
 * recurring-payment fields (no recurringProcessingModel/storePaymentMethodMode — there's no
 * subscription to renew here).
 *
 * Model differences from Stripe, by necessity:
 *  - checkoutUrl holds a JSON blob {"sessionId":...,"sessionData":...}, not a redirect URL —
 *    same trick identity-service's AdyenPaymentAdapter uses for clientSecret. Adyen's Sessions
 *    response powers the frontend's embedded Drop-in/Web Component, not a hosted redirect.
 *  - No shipping-address collection: Adyen's Drop-in has no built-in address step the way
 *    Stripe Checkout's shipping_address_collection does, so an Adyen-paid order's
 *    shippingAddress stays null after webhook completion — a real, documented gap (fulfillment
 *    would need a frontend address form wired to Adyen's deliveryAddress field, which is
 *    outside this backend repo's scope).
 *  - Same abandoned-checkout gap as PayPal: Adyen's AUTHORISATION webhook only fires on a
 *    completed payment, nothing fires for an abandoned session — covered by
 *    StaleOrderSweepJob, not a webhook.
 */
@Component
public class AdyenCheckoutAdapter implements PaymentPort {

    private final String apiKey;
    private final String merchantAccount;
    private final String hmacKey;
    private final String currency;
    private final ObjectMapper objectMapper;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;
    private final HMACValidator hmacValidator = new HMACValidator();
    private final WebhookHandler webhookHandler = new WebhookHandler();

    public AdyenCheckoutAdapter(
        @Value("${adyen.api-key:}") String apiKey,
        @Value("${adyen.merchant-account:}") String merchantAccount,
        @Value("${adyen.shop-hmac-key:}") String hmacKey,
        @Value("${adyen.environment:TEST}") String environment,
        @Value("${adyen.currency:cad}") String currency,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.merchantAccount = merchantAccount;
        this.hmacKey = hmacKey;
        this.currency = currency.toUpperCase();
        this.objectMapper = objectMapper;

        Client client = apiKey.isBlank()
            ? new Client("", Environment.TEST)
            : new Client(apiKey, "LIVE".equalsIgnoreCase(environment) ? Environment.LIVE : Environment.TEST);
        // Same timeout discipline as StripeCheckoutAdapter / identity-service's AdyenPaymentAdapter.
        client.setTimeouts(10_000, 15_000);
        this.paymentsApi = new PaymentsApi(client);
        this.modificationsApi = new ModificationsApi(client);
    }

    @Override
    public String name() {
        return "adyen";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !merchantAccount.isBlank();
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
            long totalCents = lines.stream().mapToLong(l -> l.unitPriceCents() * l.quantity()).sum();

            CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
            request.setMerchantAccount(merchantAccount);
            request.setReference(orderId.toString());
            request.setAmount(new Amount().currency(currency).value(totalCents));
            request.setReturnUrl(successUrl);
            if (customerEmail != null && !customerEmail.isBlank()) {
                request.setShopperEmail(customerEmail);
            }

            CreateCheckoutSessionResponse response = paymentsApi.sessions(request);
            String checkoutUrl = objectMapper.writeValueAsString(
                Map.of("sessionId", response.getId(), "sessionData", response.getSessionData()));
            return Optional.of(new CheckoutSession(response.getId(), checkoutUrl));
        } catch (Exception e) {
            throw new IllegalStateException("Adyen checkout session failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<RefundResult> refund(String paymentIntentId, Long amountCents, String idempotencyKey) {
        if (!isConfigured() || paymentIntentId == null || paymentIntentId.isBlank()) {
            return Optional.empty();
        }
        try {
            PaymentRefundRequest request = new PaymentRefundRequest();
            request.setMerchantAccount(merchantAccount);
            request.setReference(idempotencyKey);
            if (amountCents != null) {
                request.setAmount(new Amount().currency(currency).value(amountCents));
            }
            RequestOptions options = new RequestOptions().idempotencyKey(idempotencyKey);
            PaymentRefundResponse response =
                modificationsApi.refundCapturedPayment(paymentIntentId, request, options);
            return Optional.of(new RefundResult(
                response.getPspReference(), response.getStatus().getValue(), amountCents));
        } catch (Exception e) {
            throw new IllegalStateException("Adyen refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PaymentEvent> verifyWebhook(String payload, Map<String, String> headers) {
        if (!isConfigured() || hmacKey.isBlank()) {
            return Optional.empty();
        }
        try {
            NotificationRequest notification = webhookHandler.handleNotificationJson(payload);
            List<NotificationRequestItem> items = notification.getNotificationItems();
            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }
            NotificationRequestItem item = items.get(0);
            if (!hmacValidator.validateHMAC(item, hmacKey)) {
                return Optional.empty();
            }
            if (!"AUTHORISATION".equals(item.getEventCode()) || !item.isSuccess()) {
                return Optional.empty();
            }
            // merchantReference is the orderId we set as `reference` in createCheckoutSession.
            String orderId = item.getMerchantReference();
            if (orderId == null) return Optional.empty();
            // No shippingAddress: see class-level doc comment — Adyen's Drop-in collects no
            // address here.
            return Optional.of(new PaymentEvent(
                PaymentEventType.SESSION_COMPLETED, orderId, item.getPspReference(), null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
