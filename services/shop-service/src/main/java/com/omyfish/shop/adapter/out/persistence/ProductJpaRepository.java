package com.omyfish.shop.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {
    List<ProductJpaEntity> findByActiveTrue();

    @Modifying
    @Query("UPDATE ProductJpaEntity p SET p.stockQuantity = p.stockQuantity - :qty "
        + "WHERE p.id = :id AND p.stockQuantity >= :qty")
    int decreaseStockIfAvailable(@Param("id") UUID id, @Param("qty") int qty);
}
