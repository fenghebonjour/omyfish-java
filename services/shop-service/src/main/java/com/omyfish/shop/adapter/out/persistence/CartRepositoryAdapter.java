package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.port.out.CartRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpaRepository;

    public CartRepositoryAdapter(CartJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Cart> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).map(CartJpaEntity::toDomain);
    }

    @Override
    public Optional<Cart> findByGuestToken(UUID guestToken) {
        return jpaRepository.findByGuestToken(guestToken).map(CartJpaEntity::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        return jpaRepository.save(CartJpaEntity.from(cart)).toDomain();
    }

    @Override
    public void deleteByGuestToken(UUID guestToken) {
        jpaRepository.deleteByGuestToken(guestToken);
    }
}
