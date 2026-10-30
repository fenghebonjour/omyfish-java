package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;

import java.util.UUID;

/**
 * Plain, Jackson-friendly mirror of {@link Product} used only for cache (de)serialization.
 * {@code Product} has a private constructor and no setters, so it can't round-trip through
 * Jackson directly — keeping that conversion here instead of adding Jackson annotations to the
 * domain model.
 *
 * <p>Must be {@code public}, not package-private: Jackson Afterburner (config/AppConfig.java)
 * generates bytecode accessors for each (de)serialized type under its own ClassLoader, and a
 * package-private class is a different runtime package to that ClassLoader even with the same
 * package name — Afterburner's generated deserializer then fails with IllegalAccessError trying
 * to even reference the class, not just its fields.
 */
public class CachedProduct {

    private UUID id;
    private String name;
    private String description;
    private long priceCents;
    private String imageUrl;
    private String imageStorageKey;
    private int stockQuantity;
    private boolean active;

    public CachedProduct() {
    }

    static CachedProduct from(Product p) {
        CachedProduct c = new CachedProduct();
        c.id = p.getId();
        c.name = p.getName();
        c.description = p.getDescription();
        c.priceCents = p.getPriceCents();
        c.imageUrl = p.getImageUrl();
        c.imageStorageKey = p.getImageStorageKey();
        c.stockQuantity = p.getStockQuantity();
        c.active = p.isActive();
        return c;
    }

    Product toDomain() {
        return Product.reconstitute(id, name, description, priceCents, imageUrl, imageStorageKey, stockQuantity, active);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageStorageKey() { return imageStorageKey; }
    public void setImageStorageKey(String imageStorageKey) { this.imageStorageKey = imageStorageKey; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
