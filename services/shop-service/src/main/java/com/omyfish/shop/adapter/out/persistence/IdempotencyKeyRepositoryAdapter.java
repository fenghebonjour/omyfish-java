package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.IdempotencyRecord;
import com.omyfish.shop.domain.port.out.IdempotencyConflictException;
import com.omyfish.shop.domain.port.out.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpa;

    public IdempotencyKeyRepositoryAdapter(IdempotencyKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key, String endpoint) {
        return jpa.findByIdempotencyKeyAndEndpoint(key, endpoint).map(IdempotencyKeyJpaEntity::toDomain);
    }

    @Override
    public IdempotencyRecord reserve(String key, String endpoint, String ownerKey) {
        try {
            return jpa.save(IdempotencyKeyJpaEntity.from(IdempotencyRecord.reserve(key, endpoint, ownerKey)))
                .toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyConflictException(
                "A request with idempotency key '" + key + "' is already in progress");
        }
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return jpa.save(IdempotencyKeyJpaEntity.from(record)).toDomain();
    }

    @Override
    public void delete(IdempotencyRecord record) {
        jpa.deleteById(record.getId());
    }
}
