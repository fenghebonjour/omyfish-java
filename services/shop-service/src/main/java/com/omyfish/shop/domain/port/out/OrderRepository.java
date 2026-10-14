package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Optional<Order> findByStripeSessionId(String stripeSessionId);
    List<Order> findByUserId(UUID userId);
    List<Order> findAll();
}
