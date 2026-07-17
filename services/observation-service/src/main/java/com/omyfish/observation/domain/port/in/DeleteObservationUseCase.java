package com.omyfish.observation.domain.port.in;

import java.util.UUID;

public interface DeleteObservationUseCase {
    /** Deletes the observation if it exists and belongs to the user; returns whether it did. */
    boolean delete(UUID id, UUID userId);
}
