package com.omyfish.shop.domain.model;

import com.omyfish.shop.domain.exception.CartEmptyException;
import com.omyfish.shop.domain.model.valueobject.CartItem;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shared.domain.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Cart extends Entity<UUID> {

    private final UUID userId;
    private final UUID guestToken;
    private final List<CartItem> items = new ArrayList<>();

    private Cart(UUID id, UUID userId, UUID guestToken) {
        super(id);
        if ((userId == null) == (guestToken == null)) {
            throw new IllegalArgumentException("Cart requires exactly one of userId or guestToken");
        }
        this.userId = userId;
        this.guestToken = guestToken;
    }

    public static Cart createEmpty(CartOwner owner) {
        return new Cart(UUID.randomUUID(), owner.userId(), owner.guestToken());
    }

    public static Cart reconstitute(UUID id, UUID userId, UUID guestToken, List<CartItem> items) {
        Cart cart = new Cart(id, userId, guestToken);
        cart.items.addAll(items);
        return cart;
    }

    public void addItem(UUID productId, int quantity) {
        items.stream()
            .filter(i -> i.productId().equals(productId))
            .findFirst()
            .ifPresentOrElse(
                existing -> setQuantity(productId, existing.quantity() + quantity),
                () -> items.add(new CartItem(productId, quantity)));
    }

    public void setQuantity(UUID productId, int quantity) {
        items.removeIf(i -> i.productId().equals(productId));
        if (quantity > 0) {
            items.add(new CartItem(productId, quantity));
        }
    }

    public void clear() {
        items.clear();
    }

    // Folds another cart's items (e.g. a guest cart being merged on login) into this one,
    // summing quantities for products already present.
    public void mergeFrom(Cart other) {
        other.getItems().forEach(item -> addItem(item.productId(), item.quantity()));
    }

    public void requireNotEmpty() {
        if (items.isEmpty()) {
            throw new CartEmptyException(userId, guestToken);
        }
    }

    public boolean isGuest() { return guestToken != null; }
    public UUID getUserId() { return userId; }
    public UUID getGuestToken() { return guestToken; }
    public List<CartItem> getItems() { return List.copyOf(items); }
}
