package com.omyfish.observation.domain.event;

import com.omyfish.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public class ObservationCreatedEvent extends DomainEvent {

    private final UUID observationId;
    private final UUID userId;
    private final String speciesName;
    private final Double latitude;
    private final Double longitude;
    private final String imageStorageKey;
    private final Instant observedAt;

    public ObservationCreatedEvent(
        UUID observationId,
        UUID userId,
        String speciesName,
        Double latitude,
        Double longitude,
        String imageStorageKey,
        Instant observedAt
    ) {
        super("observation.created");
        this.observationId = observationId;
        this.userId = userId;
        this.speciesName = speciesName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageStorageKey = imageStorageKey;
        this.observedAt = observedAt;
    }

    public UUID getObservationId() { return observationId; }
    public UUID getUserId() { return userId; }
    public String getSpeciesName() { return speciesName; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getImageStorageKey() { return imageStorageKey; }
    public Instant getObservedAt() { return observedAt; }
}
