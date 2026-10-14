package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.util.List;
import java.util.UUID;

public class ProductService implements ListProductsUseCase, GetProductUseCase {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Product> listActive() {
        return repository.findAllActive();
    }

    @Override
    public Product get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
