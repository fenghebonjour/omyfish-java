package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.valueobject.CartOwner;

import java.util.UUID;

public interface AddToCartUseCase {
    Cart addItem(CartOwner owner, UUID productId, int quantity);
}
