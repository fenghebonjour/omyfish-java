package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Product;

import java.util.List;

public interface ListProductsUseCase {
    List<Product> listActive();
}
