package com.omyfish.shop.domain.exception;

import java.util.UUID;

public class CartEmptyException extends RuntimeException {
    public CartEmptyException(UUID userId, UUID guestToken) {
        super(userId != null ? "Cart is empty for user: " + userId : "Cart is empty for guest: " + guestToken);
    }
}
