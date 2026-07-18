package com.omyfish.identity.domain.port.out;

import com.omyfish.identity.domain.model.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    Subscription save(Subscription subscription);
    Optional<Subscription> findByUserId(UUID userId);
    Optional<Subscription> findByStripeCustomerId(String customerId);
    List<Subscription> findAll();
}
