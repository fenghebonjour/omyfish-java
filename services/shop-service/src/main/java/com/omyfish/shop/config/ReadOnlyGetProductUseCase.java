package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Marks this read as explicitly read-only so {@link ReadWriteRoutingDataSource} sends its JDBC
 * connection to the replica rather than the primary — see that class for why "no transaction at
 * all" deliberately does NOT also route here.
 */
class ReadOnlyGetProductUseCase implements GetProductUseCase {

    private final GetProductUseCase delegate;

    ReadOnlyGetProductUseCase(GetProductUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return delegate.get(id);
    }
}
