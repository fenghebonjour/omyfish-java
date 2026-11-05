package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.UploadProductImageUseCase;

import java.io.InputStream;
import java.util.UUID;

class CacheEvictingUploadProductImageUseCase implements UploadProductImageUseCase {

    private final UploadProductImageUseCase delegate;
    private final ProductCache cache;

    CacheEvictingUploadProductImageUseCase(UploadProductImageUseCase delegate, ProductCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Product uploadImage(
        UUID productId, InputStream data, long size, String contentType, String originalFilename
    ) {
        Product product = delegate.uploadImage(productId, data, size, contentType, originalFilename);
        // evictProduct() also evicts the list cache (the listing embeds each product's imageUrl).
        cache.evictProduct(productId);
        return product;
    }
}
