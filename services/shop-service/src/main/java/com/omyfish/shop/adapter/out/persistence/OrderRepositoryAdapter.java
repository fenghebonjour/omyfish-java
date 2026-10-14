package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.port.out.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        return jpaRepository.save(OrderJpaEntity.from(order)).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByStripeSessionId(String stripeSessionId) {
        return jpaRepository.findByStripeSessionId(stripeSessionId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }
}
