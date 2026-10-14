package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.valueobject.CartOwner;

public interface ViewCartUseCase {
    Cart getOrCreateCart(CartOwner owner);
}
