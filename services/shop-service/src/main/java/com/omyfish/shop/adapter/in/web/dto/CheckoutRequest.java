package com.omyfish.shop.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// guestEmail is required for guest checkout (no X-User-Id) and ignored for account checkout —
// that conditional rule stays a manual check in CheckoutController, not a bean-validation
// annotation here, since it depends on which header is present.
public record CheckoutRequest(@NotBlank String successUrl, @NotBlank String cancelUrl, String guestEmail) {
}
