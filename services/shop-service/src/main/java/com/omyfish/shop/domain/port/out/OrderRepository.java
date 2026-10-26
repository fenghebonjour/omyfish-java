package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Optional<Order> findByStripeSessionId(String stripeSessionId);
    List<Order> findByUserId(UUID userId);
    List<Order> findAll();

    // PENDING_PAYMENT orders created before cutoff — abandoned checkouts a processor never
    // reported back on (e.g. PayPal/Adyen, which have no expiry webhook at all), for
    // StaleOrderSweepJob to cancel and restock.
    List<Order> findPendingPaymentOlderThan(Instant cutoff);
}
