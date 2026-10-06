package com.omyfish.identity.domain.port.out;

import com.omyfish.identity.domain.model.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository {
    Optional<IdempotencyRecord> find(String key, String endpoint);

    /** Throws IdempotencyConflictException if a reservation for this key+endpoint already exists. */
    IdempotencyRecord reserve(String key, String endpoint, UUID userId);

    IdempotencyRecord save(IdempotencyRecord record);
    void delete(IdempotencyRecord record);
}
