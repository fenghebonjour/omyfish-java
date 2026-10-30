package com.omyfish.shop.config;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.GetProductUseCase;

import java.util.UUID;

class CachingGetProductUseCase implements GetProductUseCase {

    private final GetProductUseCase delegate;
    private final ProductCache cache;

    CachingGetProductUseCase(GetProductUseCase delegate, ProductCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Product get(UUID id) {
        if (cache.isKnownMissing(id)) {
            throw new ProductNotFoundException(id);
        }
        return cache.getProduct(id).orElseGet(() -> {
            try {
                Product product = delegate.get(id);
                cache.putProduct(product);
                return product;
            } catch (ProductNotFoundException e) {
                cache.markMissing(id);
                throw e;
            }
        });
    }
}
