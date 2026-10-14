package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Product;

import java.util.UUID;

public interface GetProductUseCase {
    Product get(UUID id);
}
