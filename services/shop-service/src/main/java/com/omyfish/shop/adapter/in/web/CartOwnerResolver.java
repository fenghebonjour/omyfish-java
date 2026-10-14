package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.domain.model.valueobject.CartOwner;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// Shared by CartController and CheckoutController: both accept either an authenticated
// X-User-Id (set by the gateway from a valid JWT) or an anonymous X-Cart-Token, never both.
final class CartOwnerResolver {

    private CartOwnerResolver() {}

    static CartOwner resolve(String userIdHeader, String cartTokenHeader) {
        boolean hasUser = userIdHeader != null && !userIdHeader.isBlank();
        boolean hasGuest = cartTokenHeader != null && !cartTokenHeader.isBlank();
        if (hasUser == hasGuest) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Provide exactly one of X-User-Id or X-Cart-Token");
        }
        return hasUser ? CartOwner.ofUser(UUID.fromString(userIdHeader)) : CartOwner.ofGuest(UUID.fromString(cartTokenHeader));
    }
}
