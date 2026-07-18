package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByUserId(UUID userId);
    Optional<Subscription> findByStripeCustomerId(String customerId);
}
