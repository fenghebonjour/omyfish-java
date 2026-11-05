package com.omyfish.shop.domain.model;

import com.omyfish.shared.domain.Entity;

import java.util.UUID;

public class Product extends Entity<UUID> {

    private final String name;
    private final String description;
    private final long priceCents;
    private String imageUrl;
    private String imageStorageKey;
    private int stockQuantity;
    private final boolean active;

    private Product(
        UUID id, String name, String description, long priceCents,
        String imageUrl, String imageStorageKey, int stockQuantity, boolean active
    ) {
        super(id);
        this.name = name;
        this.description = description;
        this.priceCents = priceCents;
        this.imageUrl = imageUrl;
        this.imageStorageKey = imageStorageKey;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    // Seeded/placeholder products have no uploaded photo yet (imageUrl is an inline data: URI
    // instead) — this overload keeps every existing caller (tests included) oblivious to the
    // image-upload feature.
    public static Product reconstitute(
        UUID id, String name, String description, long priceCents,
        String imageUrl, int stockQuantity, boolean active
    ) {
        return new Product(id, name, description, priceCents, imageUrl, null, stockQuantity, active);
    }

    public static Product reconstitute(
        UUID id, String name, String description, long priceCents,
        String imageUrl, String imageStorageKey, int stockQuantity, boolean active
    ) {
        return new Product(id, name, description, priceCents, imageUrl, imageStorageKey, stockQuantity, active);
    }

    // Called once an admin uploads a real photo — imageUrl now points at this service's own
    // serve-image endpoint (never at MinIO directly), imageStorageKey at the underlying object.
    public void setImage(String imageUrl, String imageStorageKey) {
        this.imageUrl = imageUrl;
        this.imageStorageKey = imageStorageKey;
    }

    public void restock(int quantity) {
        stockQuantity += quantity;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPriceCents() { return priceCents; }
    public String getImageUrl() { return imageUrl; }
    public String getImageStorageKey() { return imageStorageKey; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
}
