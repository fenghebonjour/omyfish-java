package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    List<Product> findAllActive();
    Optional<Product> findById(UUID id);
    Product save(Product product);
}
