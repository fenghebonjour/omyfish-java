package com.omyfish.shared.events;

import java.time.Instant;
import java.util.UUID;

public record ObservationCreatedEvent(
    String eventId,
    Instant occurredOn,
    UUID observationId,
    UUID userId,
    String speciesName,
    Double latitude,
    Double longitude,
    String imageStorageKey,
    Instant observedAt
) {
    public ObservationCreatedEvent(
        UUID observationId,
        UUID userId,
        String speciesName,
        Double latitude,
        Double longitude,
        String imageStorageKey,
        Instant observedAt
    ) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            observationId,
            userId,
            speciesName,
            latitude,
            longitude,
            imageStorageKey,
            observedAt
        );
    }

    public static final String ROUTING_KEY = "observation.created";
    public static final String EXCHANGE = "omyfish.observations";
}
