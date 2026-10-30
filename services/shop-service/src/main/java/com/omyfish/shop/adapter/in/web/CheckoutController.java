package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.CheckoutRequest;
import com.omyfish.shop.config.FeatureFlags;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shop")
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;
    private final ConfirmPaymentWebhookUseCase confirmPaymentWebhookUseCase;
    private final FeatureFlags featureFlags;

    public CheckoutController(
        CheckoutUseCase checkoutUseCase, ConfirmPaymentWebhookUseCase confirmPaymentWebhookUseCase,
        FeatureFlags featureFlags
    ) {
        this.checkoutUseCase = checkoutUseCase;
        this.confirmPaymentWebhookUseCase = confirmPaymentWebhookUseCase;
        this.featureFlags = featureFlags;
    }

    // @RateLimiter is admission control ahead of everything else below — a flash-sale spike
    // gets a fast, cheap 429 instead of spending a Hikari connection and a reservation attempt
    // on a request that was going to be shed anyway.
    @PostMapping("/checkout")
    @RateLimiter(name = "checkout")
    public ResponseEntity<Map<String, String>> checkout(
        @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
        @RequestHeader(value = "X-Cart-Token", required = false) String cartTokenHeader,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CheckoutRequest request
    ) {
        if (!featureFlags.isCheckoutEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Checkout is temporarily paused");
        }
        CartOwner owner = CartOwnerResolver.resolve(userIdHeader, cartTokenHeader);
        if (owner.isGuest() && (request.guestEmail() == null || request.guestEmail().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guestEmail is required for guest checkout");
        }

        var result = checkoutUseCase.checkout(
            owner, owner.isGuest() ? request.guestEmail() : null, request.successUrl(), request.cancelUrl(),
            idempotencyKey);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("orderId", result.orderId().toString());
        response.put("checkoutUrl", result.checkoutUrl());
        return ResponseEntity.ok(response);
    }

    // Public — Stripe/PayPal/Adyen call this directly, no account/JWT involved;
    // signature-verified inside the use case instead. Whitelisted in the gateway AuthFilter.
    @PostMapping("/webhook/{processor}")
    public ResponseEntity<Map<String, Boolean>> webhook(
        @PathVariable String processor, @RequestBody String payload, @RequestHeader Map<String, String> rawHeaders
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        rawHeaders.forEach((key, value) -> headers.put(key.toLowerCase(), value));
        boolean handled = confirmPaymentWebhookUseCase.handleWebhook(processor, payload, headers);
        return ResponseEntity.ok(Map.of("handled", handled));
    }
}
