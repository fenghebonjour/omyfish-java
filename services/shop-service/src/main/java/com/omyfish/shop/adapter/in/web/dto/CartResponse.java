package com.omyfish.shop.adapter.in.web.dto;

import com.omyfish.shop.domain.model.Cart;

import java.util.List;
import java.util.UUID;

public record CartResponse(UUID cartId, List<Item> items) {

    public record Item(UUID productId, int quantity) {}

    public static CartResponse from(Cart cart) {
        List<Item> items = cart.getItems().stream()
            .map(i -> new Item(i.productId(), i.quantity()))
            .toList();
        return new CartResponse(cart.getId(), items);
    }
}
