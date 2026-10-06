package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
}
