package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.valueobject.CartOwner;

import java.util.UUID;

public interface CheckoutUseCase {
    // replayed distinguishes an idempotent-replay response from a freshly created order — the
    // adapter-layer metering decorator (config/MeteredCheckoutUseCase) uses it to count
    // idempotent replays separately from new checkouts; the HTTP response body ignores it.
    record CheckoutResult(UUID orderId, String checkoutUrl, boolean replayed) {}

    // guestEmail is required when owner.isGuest(), and ignored otherwise. idempotencyKey makes a
    // retried call with the same key replay the original order/checkout-url instead of
    // reserving stock and creating an order a second time.
    CheckoutResult checkout(CartOwner owner, String guestEmail, String successUrl, String cancelUrl, String idempotencyKey);
}
