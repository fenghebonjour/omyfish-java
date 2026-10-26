package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
    Optional<OrderJpaEntity> findByStripeSessionId(String stripeSessionId);
    List<OrderJpaEntity> findByUserId(UUID userId);
    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc();
    List<OrderJpaEntity> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);
}
