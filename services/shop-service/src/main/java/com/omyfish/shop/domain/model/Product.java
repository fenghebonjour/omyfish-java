package com.omyfish.shop.domain.model;

import com.omyfish.shop.domain.exception.InsufficientStockException;
import com.omyfish.shared.domain.Entity;

import java.util.UUID;

public class Product extends Entity<UUID> {

    private final String name;
    private final String description;
    private final long priceCents;
    private final String imageUrl;
    private int stockQuantity;
    private final boolean active;

    private Product(
        UUID id, String name, String description, long priceCents,
        String imageUrl, int stockQuantity, boolean active
    ) {
        super(id);
        this.name = name;
        this.description = description;
        this.priceCents = priceCents;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    public static Product reconstitute(
        UUID id, String name, String description, long priceCents,
        String imageUrl, int stockQuantity, boolean active
    ) {
        return new Product(id, name, description, priceCents, imageUrl, stockQuantity, active);
    }

    public void decreaseStock(int quantity) {
        if (quantity > stockQuantity) {
            throw new InsufficientStockException(getId(), quantity, stockQuantity);
        }
        stockQuantity -= quantity;
    }

    public void restock(int quantity) {
        stockQuantity += quantity;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPriceCents() { return priceCents; }
    public String getImageUrl() { return imageUrl; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
}
