package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    Optional<Cart> findByUserId(UUID userId);
    Optional<Cart> findByGuestToken(UUID guestToken);
    Cart save(Cart cart);
    void deleteByGuestToken(UUID guestToken);
}
