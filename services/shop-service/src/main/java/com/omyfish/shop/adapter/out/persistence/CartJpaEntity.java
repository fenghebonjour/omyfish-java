package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Cart;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts", schema = "shop")
class CartJpaEntity {

    @Id
    private UUID id;

    @Column(unique = true)
    private UUID userId;

    @Column(unique = true)
    private UUID guestToken;

    @ElementCollection
    @CollectionTable(name = "cart_items", schema = "shop", joinColumns = @JoinColumn(name = "cart_id"))
    private List<CartItemEmbeddable> items = new ArrayList<>();

    protected CartJpaEntity() {}

    static CartJpaEntity from(Cart cart) {
        CartJpaEntity e = new CartJpaEntity();
        e.id = cart.getId();
        e.userId = cart.getUserId();
        e.guestToken = cart.getGuestToken();
        e.items = cart.getItems().stream().map(CartItemEmbeddable::from).toList();
        return e;
    }

    Cart toDomain() {
        return Cart.reconstitute(id, userId, guestToken, items.stream().map(CartItemEmbeddable::toDomain).toList());
    }
}
