package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "products", schema = "shop")
class ProductJpaEntity {

    @Id
    private UUID id;
    private String name;
    @Column(length = 2000)
    private String description;
    private long priceCents;
    private String imageUrl;
    private String imageStorageKey;
    private int stockQuantity;
    private boolean active;

    protected ProductJpaEntity() {}

    static ProductJpaEntity from(Product p) {
        ProductJpaEntity e = new ProductJpaEntity();
        e.id = p.getId();
        e.name = p.getName();
        e.description = p.getDescription();
        e.priceCents = p.getPriceCents();
        e.imageUrl = p.getImageUrl();
        e.imageStorageKey = p.getImageStorageKey();
        e.stockQuantity = p.getStockQuantity();
        e.active = p.isActive();
        return e;
    }

    Product toDomain() {
        return Product.reconstitute(
            id, name, description, priceCents, imageUrl, imageStorageKey, stockQuantity, active);
    }
}
