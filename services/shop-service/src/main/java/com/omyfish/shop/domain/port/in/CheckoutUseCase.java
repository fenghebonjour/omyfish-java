package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.valueobject.CartOwner;

import java.util.UUID;

public interface CheckoutUseCase {
    record CheckoutResult(UUID orderId, String checkoutUrl) {}

    // guestEmail is required when owner.isGuest(), and ignored otherwise.
    CheckoutResult checkout(CartOwner owner, String guestEmail, String successUrl, String cancelUrl);
}
