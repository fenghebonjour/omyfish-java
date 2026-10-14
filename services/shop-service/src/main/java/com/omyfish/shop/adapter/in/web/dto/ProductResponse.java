package com.omyfish.shop.adapter.in.web.dto;

import com.omyfish.shop.domain.model.Product;

import java.util.UUID;

public record ProductResponse(
    UUID id, String name, String description, long priceCents, String imageUrl, int stockQuantity
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
            p.getId(), p.getName(), p.getDescription(), p.getPriceCents(), p.getImageUrl(), p.getStockQuantity());
    }
}
