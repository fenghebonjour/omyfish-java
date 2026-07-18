package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpa;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Subscription save(Subscription subscription) {
        return jpa.save(subscription);
    }

    @Override
    public Optional<Subscription> findByUserId(UUID userId) {
        return jpa.findByUserId(userId);
    }

    @Override
    public Optional<Subscription> findByStripeCustomerId(String customerId) {
        return jpa.findByStripeCustomerId(customerId);
    }

    @Override
    public List<Subscription> findAll() {
        return jpa.findAll();
    }
}
