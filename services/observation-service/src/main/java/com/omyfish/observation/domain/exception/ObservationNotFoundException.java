package com.omyfish.observation.domain.exception;

import java.util.UUID;

public class ObservationNotFoundException extends RuntimeException {
    public ObservationNotFoundException(UUID id) {
        super("Observation not found: " + id);
    }
}
