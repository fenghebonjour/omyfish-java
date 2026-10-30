package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyKeyRepository {
    Optional<IdempotencyRecord> find(String key, String endpoint);

    /** Throws IdempotencyConflictException if a reservation for this key+endpoint already exists. */
    IdempotencyRecord reserve(String key, String endpoint, String ownerKey);

    IdempotencyRecord save(IdempotencyRecord record);
    void delete(IdempotencyRecord record);
}
