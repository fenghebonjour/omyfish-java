package com.omyfish.shop.adapter.in.web.dto;

// guestEmail is required for guest checkout (no X-User-Id) and ignored for account checkout.
public record CheckoutRequest(String successUrl, String cancelUrl, String guestEmail) {
}
