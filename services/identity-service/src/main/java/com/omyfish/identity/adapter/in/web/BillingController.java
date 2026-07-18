package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.TokenPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billing;
    private final PaymentPort payments;
    private final TokenPort tokenPort;

    public BillingController(BillingService billing, PaymentPort payments, TokenPort tokenPort) {
        this.billing = billing;
        this.payments = payments;
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

    @PostMapping("/checkout")
    public Map<String, String> checkout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody CheckoutRequest request
    ) {
        UUID userId = requireUser(authHeader);
        try {
            String url = billing.checkoutUrl(userId, request.plan())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured"));
            return Map.of("checkoutUrl", url);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (!payments.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        var event = payments.verifyWebhook(payload, signature == null ? "" : signature)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid webhook signature"));
        return Map.of("handled", billing.applyEvent(event));
    }

    record CheckoutRequest(String plan) {}

    record SubscriptionResponse(String status, String plan,
                                Instant trialEnd, Instant currentPeriodEnd) {
        static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(
                s.getEffectiveStatus(), s.getPlan(), s.getTrialEnd(), s.getCurrentPeriodEnd());
        }
    }
}
