package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** See {@link ReadOnlyGetProductUseCase} — same reasoning, for the catalog listing. */
class ReadOnlyListProductsUseCase implements ListProductsUseCase {

    private final ListProductsUseCase delegate;

    ReadOnlyListProductsUseCase(ListProductsUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> listActive() {
        return delegate.listActive();
    }
}
