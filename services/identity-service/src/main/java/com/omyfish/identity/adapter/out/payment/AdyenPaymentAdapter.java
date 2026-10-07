package com.omyfish.identity.adapter.out.payment;

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
import com.omyfish.identity.domain.port.out.PaymentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adyen Checkout Sessions — a sibling acquirer to StripePaymentAdapter, not a Stripe payment
 * method. Uses the official com.adyen:adyen-java-api-library for typed requests and HMAC
 * webhook validation (HMACValidator/WebhookHandler), rather than hand-rolled REST.
 *
 * Model differences from Stripe, by necessity rather than by choice:
 *  - Adyen has no subscription object at all. The first payment stores a payment method
 *    (recurringProcessingModel=SUBSCRIPTION, storePaymentMethodMode=ENABLED); actually renewing
 *    on a monthly/yearly schedule requires us to charge that stored payment method ourselves on
 *    a schedule — that recurring-charge scheduler is a separate follow-up feature, not built
 *    here (see BACKLOG.md). subscriptionId is therefore our own generated reference, not
 *    Adyen's.
 *  - clientSecret holds a small JSON blob {"sessionId":...,"sessionData":...} — Adyen's web
 *    Components need both values, not one string.
 *  - createSetupIntent uses a zero-amount session as a known rough edge: a true zero-amount
 *    verification isn't uniformly supported across every card network/region.
 *  - setDefaultPaymentMethod is a documented no-op (see StripePaymentAdapter's PayPal sibling
 *    for the same reasoning) — and in this design Adyen's webhook mapping never emits
 *    payment_method_attached anyway, so it's genuinely unreachable here, not a hidden gap.
 *  - refundSubscription requires lastPaymentReference (Adyen's pspReference of the last
 *    captured payment) since Adyen has no subscription object to look it up from.
 */
@Component
public class AdyenPaymentAdapter implements PaymentPort {

    private final String apiKey;
    private final String merchantAccount;
    private final String hmacKey;
    private final String currency;
    private final long priceMonthlyCents;
    private final long priceYearlyCents;
    private final String returnUrl;
    private final ObjectMapper objectMapper;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;
    private final HMACValidator hmacValidator = new HMACValidator();
    private final WebhookHandler webhookHandler = new WebhookHandler();

    public AdyenPaymentAdapter(
        @Value("${adyen.api-key:}") String apiKey,
        @Value("${adyen.merchant-account:}") String merchantAccount,
        @Value("${adyen.hmac-key:}") String hmacKey,
        @Value("${adyen.environment:TEST}") String environment,
        @Value("${adyen.currency:CAD}") String currency,
        @Value("${adyen.price-monthly-cents:500}") long priceMonthlyCents,
        @Value("${adyen.price-yearly-cents:2900}") long priceYearlyCents,
        @Value("${adyen.return-url:}") String returnUrl,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.merchantAccount = merchantAccount;
        this.hmacKey = hmacKey;
        this.currency = currency;
        this.priceMonthlyCents = priceMonthlyCents;
        this.priceYearlyCents = priceYearlyCents;
        this.returnUrl = returnUrl;
        this.objectMapper = objectMapper;

        Client client = apiKey.isBlank()
            ? new Client("", Environment.TEST)
            : new Client(apiKey, "LIVE".equalsIgnoreCase(environment) ? Environment.LIVE : Environment.TEST);
        // Same timeout discipline as AIServiceAdapter's WebClient fix — a slow (not down) Adyen
        // must not hang a request indefinitely.
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
    public Optional<SubscriptionIntent> createSubscriptionIntent(
        UUID userId, String email, String plan, String idempotencyKey
    ) {
        long priceCents = "yearly".equals(plan) ? priceYearlyCents : priceMonthlyCents;
        if (!isConfigured() || returnUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String reference = userId + ":" + plan + ":" + UUID.randomUUID();
            CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
            request.setMerchantAccount(merchantAccount);
            request.setReference(reference);
            request.setAmount(new Amount().currency(currency).value(priceCents));
            request.setReturnUrl(returnUrl);
            request.setShopperReference(userId.toString());
            request.setShopperEmail(email);
            request.setRecurringProcessingModel(
                CreateCheckoutSessionRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
            request.setStorePaymentMethodMode(
                CreateCheckoutSessionRequest.StorePaymentMethodModeEnum.ENABLED);

            CreateCheckoutSessionResponse response = paymentsApi.sessions(request);
            String clientSecret = objectMapper.writeValueAsString(
                Map.of("sessionId", response.getId(), "sessionData", response.getSessionData()));
            return Optional.of(new SubscriptionIntent(
                name(), userId.toString(), reference, clientSecret, "pending"));
        } catch (Exception e) {
            throw new IllegalStateException("Adyen checkout session failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SetupIntentResult> createSetupIntent(UUID userId, String email) {
        if (!isConfigured() || returnUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String reference = userId + ":setup:" + UUID.randomUUID();
            CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
            request.setMerchantAccount(merchantAccount);
            request.setReference(reference);
            // Known rough edge: a true zero-amount verification isn't uniformly supported
            // across every card network/region — validate against the schemes actually in
            // play before relying on this in production.
            request.setAmount(new Amount().currency(currency).value(0L));
            request.setReturnUrl(returnUrl);
            request.setShopperReference(userId.toString());
            request.setShopperEmail(email);
            request.setRecurringProcessingModel(
                CreateCheckoutSessionRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
            request.setStorePaymentMethodMode(
                CreateCheckoutSessionRequest.StorePaymentMethodModeEnum.ENABLED);

            CreateCheckoutSessionResponse response = paymentsApi.sessions(request);
            String clientSecret = objectMapper.writeValueAsString(
                Map.of("sessionId", response.getId(), "sessionData", response.getSessionData()));
            return Optional.of(new SetupIntentResult(name(), userId.toString(), clientSecret));
        } catch (Exception e) {
            throw new IllegalStateException("Adyen setup session failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setDefaultPaymentMethod(String customerId, String paymentMethodId) {
        // No-op: Adyen has no customer-level "default payment method" pointer, and this
        // design's webhook mapping never emits payment_method_attached for Adyen anyway.
    }

    @Override
    public List<ReconciliationCandidate> listRecentSubscriptions(Instant since) {
        // Not implemented: Adyen isn't live yet (BACKLOG I.6) — add this once it is. It would
        // also need Adyen's own notion of "recent" (it has no subscription object to list;
        // this would have to query captured payments by shopperReference/merchantReference
        // instead).
        return List.of();
    }

    @Override
    public Optional<RefundResult> refundSubscription(
        String subscriptionId, String lastPaymentReference, Long amountCents, String idempotencyKey
    ) {
        if (!isConfigured() || lastPaymentReference == null || lastPaymentReference.isBlank()) {
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
                modificationsApi.refundCapturedPayment(lastPaymentReference, request, options);
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
            // Adyen has no subscription-lifecycle events to speak of since it never hosts the
            // subscription itself — only a successful capture is meaningful to us here.
            if (!"AUTHORISATION".equals(item.getEventCode()) || !item.isSuccess()) {
                return Optional.empty();
            }
            // merchantReference is our own "userId:plan:uuid" reference (see
            // createSubscriptionIntent) — there's no shopperReference getter on notification
            // items, so the customer id is recovered from the reference we control instead.
            String reference = item.getMerchantReference();
            String customerId = reference != null && reference.contains(":")
                ? reference.substring(0, reference.indexOf(':')) : reference;
            return Optional.of(new PaymentEvent(
                item.getPspReference(), name(), "payment_captured",
                customerId, null, null, null, null,
                item.getPspReference()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
