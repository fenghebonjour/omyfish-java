package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Product> findAllActive() {
        return jpaRepository.findByActiveTrue().stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public Product save(Product product) {
        return jpaRepository.save(ProductJpaEntity.from(product)).toDomain();
    }

    @Override
    public boolean tryDecreaseStock(UUID id, int quantity) {
        return jpaRepository.decreaseStockIfAvailable(id, quantity) > 0;
    }
}
