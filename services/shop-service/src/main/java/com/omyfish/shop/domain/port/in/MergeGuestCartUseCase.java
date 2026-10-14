package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Cart;

import java.util.UUID;

// Folds an anonymous guest cart into the now-authenticated user's cart, called right after
// login/register when the frontend is still holding a guest cart token.
public interface MergeGuestCartUseCase {
    Cart merge(UUID userId, UUID guestToken);
}
