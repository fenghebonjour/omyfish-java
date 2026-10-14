package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.valueobject.CartOwner;

import java.util.UUID;

public interface UpdateCartItemUseCase {
    // quantity 0 removes the item from the cart.
    Cart updateQuantity(CartOwner owner, UUID productId, int quantity);
}
