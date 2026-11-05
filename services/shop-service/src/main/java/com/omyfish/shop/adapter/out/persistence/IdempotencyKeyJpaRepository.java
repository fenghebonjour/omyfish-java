package com.omyfish.shop.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {
    Optional<IdempotencyKeyJpaEntity> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
}
