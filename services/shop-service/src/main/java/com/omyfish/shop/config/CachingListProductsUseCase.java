package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;

import java.util.List;

class CachingListProductsUseCase implements ListProductsUseCase {

    private final ListProductsUseCase delegate;
    private final ProductCache cache;

    CachingListProductsUseCase(ListProductsUseCase delegate, ProductCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<Product> listActive() {
        return cache.getList().orElseGet(() -> {
            List<Product> products = delegate.listActive();
            cache.putList(products);
            return products;
        });
    }
}
