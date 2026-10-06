package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.IdempotencyRecord;
import com.omyfish.identity.domain.port.out.IdempotencyConflictException;
import com.omyfish.identity.domain.port.out.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpa;

    public IdempotencyKeyRepositoryAdapter(IdempotencyKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key, String endpoint) {
        return jpa.findByIdempotencyKeyAndEndpoint(key, endpoint);
    }

    @Override
    public IdempotencyRecord reserve(String key, String endpoint, UUID userId) {
        try {
            return jpa.save(IdempotencyRecord.reserve(key, endpoint, userId));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyConflictException(
                "A request with idempotency key '" + key + "' is already in progress");
        }
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return jpa.save(record);
    }

    @Override
    public void delete(IdempotencyRecord record) {
        jpa.delete(record);
    }
}
