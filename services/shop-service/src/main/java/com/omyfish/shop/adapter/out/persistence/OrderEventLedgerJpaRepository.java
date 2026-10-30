package com.omyfish.shop.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderEventLedgerJpaRepository extends JpaRepository<OrderEventLedgerJpaEntity, UUID> {
    boolean existsByEventIdAndProductId(String eventId, UUID productId);
}
