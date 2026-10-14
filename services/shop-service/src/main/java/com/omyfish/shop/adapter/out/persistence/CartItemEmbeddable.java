package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.valueobject.CartItem;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
class CartItemEmbeddable {

    @Column(name = "product_id")
    private UUID productId;
    private int quantity;

    protected CartItemEmbeddable() {}

    static CartItemEmbeddable from(CartItem item) {
        CartItemEmbeddable e = new CartItemEmbeddable();
        e.productId = item.productId();
        e.quantity = item.quantity();
        return e;
    }

    CartItem toDomain() {
        return new CartItem(productId, quantity);
    }
}
