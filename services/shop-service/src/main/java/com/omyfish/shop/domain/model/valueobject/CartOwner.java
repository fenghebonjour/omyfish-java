package com.omyfish.shop.domain.model.valueobject;

import java.util.UUID;

// Either an authenticated user's id or an anonymous guest's cart token, never both — the two
// identity schemes feeding cart/checkout now that guest checkout exists alongside account
// checkout (see shared/api-contracts/public-routes.yaml's shop-service entries).
public record CartOwner(UUID userId, UUID guestToken) {

    public CartOwner {
        if ((userId == null) == (guestToken == null)) {
            throw new IllegalArgumentException("CartOwner requires exactly one of userId or guestToken");
        }
    }

    public static CartOwner ofUser(UUID userId) {
        return new CartOwner(userId, null);
    }

    public static CartOwner ofGuest(UUID guestToken) {
        return new CartOwner(null, guestToken);
    }

    public boolean isGuest() {
        return guestToken != null;
    }
}
