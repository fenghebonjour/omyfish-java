package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.application.service.PaymentProcessorRegistry;
import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.IdempotencyConflictException;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.TokenPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billing;
    private final PaymentProcessorRegistry processors;
    private final TokenPort tokenPort;

    public BillingController(BillingService billing, PaymentProcessorRegistry processors, TokenPort tokenPort) {
        this.billing = billing;
        this.processors = processors;
        this.tokenPort = tokenPort;
    }

    private UUID requireUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return tokenPort.validateAccess(authHeader.substring(7))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }

    @GetMapping("/me")
    public SubscriptionResponse me(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return SubscriptionResponse.from(billing.mySubscription(requireUser(authHeader)));
    }

    @PostMapping("/portal-session")
    public Map<String, String> portalSession(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody PortalSessionRequest request
    ) {
        UUID userId = requireUser(authHeader);
        try {
            String url = billing.createPortalSession(userId, request.returnUrl())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "No self-service billing portal for this account"));
            return Map.of("url", url);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody CheckoutRequest request
    ) {
        UUID userId = requireUser(authHeader);
        try {
            PaymentPort.SubscriptionIntent intent =
                billing.startCheckout(userId, request.plan(), idempotencyKey)
                    .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No payment processor is configured"));
            Map<String, String> response = new LinkedHashMap<>();
            response.put("processor", intent.processor());
            response.put("clientSecret", intent.clientSecret());
            response.put("subscriptionId", intent.subscriptionId());
            response.put("status", intent.status());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IdempotencyConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/payment-method/setup")
    public Map<String, String> setupPaymentMethod(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = requireUser(authHeader);
        PaymentPort.SetupIntentResult intent = billing.startPaymentMethodSetup(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No payment processor is configured"));
        Map<String, String> response = new LinkedHashMap<>();
        response.put("processor", intent.processor());
        response.put("clientSecret", intent.clientSecret());
        response.put("customerId", intent.customerId());
        return response;
    }

    @PostMapping("/webhook/{processor}")
    public ResponseEntity<?> webhook(
        @PathVariable String processor,
        @RequestBody String payload,
        @RequestHeader Map<String, String> rawHeaders
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        rawHeaders.forEach((key, value) -> headers.put(key.toLowerCase(), value));

        if (!processors.exists(processor)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown payment processor: " + processor);
        }
        PaymentPort port;
        try {
            port = processors.byName(processor);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Payment processor is not configured: " + processor);
        }
        var event = port.verifyWebhook(payload, headers)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid webhook signature"));
        boolean handled = billing.applyEvent(event);

        // Adyen requires this exact literal body (not JSON) or it keeps retrying the webhook.
        if ("adyen".equals(processor)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("[accepted]");
        }
        return ResponseEntity.ok(Map.of("handled", handled));
    }

    record CheckoutRequest(String plan) {}

    record PortalSessionRequest(String returnUrl) {}

    record SubscriptionResponse(String status, String plan,
                                Instant trialEnd, Instant currentPeriodEnd, String paymentProcessor) {
        static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(
                s.getEffectiveStatus(), s.getPlan(), s.getTrialEnd(), s.getCurrentPeriodEnd(),
                s.getPaymentProcessor());
        }
    }
}
