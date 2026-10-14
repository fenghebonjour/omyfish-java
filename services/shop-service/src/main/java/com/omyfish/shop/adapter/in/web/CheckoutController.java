package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.CheckoutRequest;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
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

    public CheckoutController(
        CheckoutUseCase checkoutUseCase, ConfirmPaymentWebhookUseCase confirmPaymentWebhookUseCase
    ) {
        this.checkoutUseCase = checkoutUseCase;
        this.confirmPaymentWebhookUseCase = confirmPaymentWebhookUseCase;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(
        @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
        @RequestHeader(value = "X-Cart-Token", required = false) String cartTokenHeader,
        @RequestBody CheckoutRequest request
    ) {
        CartOwner owner = CartOwnerResolver.resolve(userIdHeader, cartTokenHeader);
        if (owner.isGuest() && (request.guestEmail() == null || request.guestEmail().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guestEmail is required for guest checkout");
        }

        var result = checkoutUseCase.checkout(
            owner, owner.isGuest() ? request.guestEmail() : null, request.successUrl(), request.cancelUrl());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("orderId", result.orderId().toString());
        response.put("checkoutUrl", result.checkoutUrl());
        return ResponseEntity.ok(response);
    }

    // Public — Stripe calls this directly, no account/JWT involved; signature-verified inside
    // the use case instead. Whitelisted in the gateway AuthFilter.
    @PostMapping("/webhook/stripe")
    public ResponseEntity<Map<String, Boolean>> webhook(
        @RequestBody String payload, @RequestHeader Map<String, String> rawHeaders
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        rawHeaders.forEach((key, value) -> headers.put(key.toLowerCase(), value));
        boolean handled = confirmPaymentWebhookUseCase.handleWebhook(payload, headers);
        return ResponseEntity.ok(Map.of("handled", handled));
    }
}
