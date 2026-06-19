package com.omyfish.observation.domain.model;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.model.valueobject.ExifMetadata;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.shared.domain.AggregateRoot;

import java.time.Instant;
import java.util.UUID;

public class Observation extends AggregateRoot<UUID> {

    private UUID userId;
    private String speciesName;
    private String scientificName;
    private double topConfidence;
    private String imageStorageKey;
    private GpsCoordinates location;
    private ExifMetadata exifMetadata;
    private String notes;
    private Instant observedAt;
    private Instant createdAt;

    private Observation(UUID id) {
        super(id);
    }

    private Observation() {
        super(UUID.randomUUID());
    }

    public static Observation reconstitute(
        UUID id, UUID userId, String speciesName, String scientificName,
        double topConfidence, String imageStorageKey, GpsCoordinates location,
        ExifMetadata exifMetadata, String notes, Instant observedAt, Instant createdAt
    ) {
        Observation obs = new Observation(id);
        obs.userId = userId;
        obs.speciesName = speciesName;
        obs.scientificName = scientificName;
        obs.topConfidence = topConfidence;
        obs.imageStorageKey = imageStorageKey;
        obs.location = location;
        obs.exifMetadata = exifMetadata;
        obs.notes = notes;
        obs.observedAt = observedAt;
        obs.createdAt = createdAt;
        return obs;
    }

    public static Observation create(
        UUID userId,
        String speciesName,
        String scientificName,
        double topConfidence,
        String imageStorageKey,
        GpsCoordinates location,
        ExifMetadata exifMetadata,
        String notes
    ) {
        Observation obs = new Observation();
        obs.userId = userId;
        obs.speciesName = speciesName;
        obs.scientificName = scientificName;
        obs.topConfidence = topConfidence;
        obs.imageStorageKey = imageStorageKey;
        obs.location = location;
        obs.exifMetadata = exifMetadata;
        obs.notes = notes;
        obs.observedAt = exifMetadata != null && exifMetadata.capturedAt() != null
            ? exifMetadata.capturedAt()
            : Instant.now();
        obs.createdAt = Instant.now();

        obs.registerEvent(new ObservationCreatedEvent(
            obs.getId(),
            userId,
            speciesName,
            location != null ? location.latitude() : null,
            location != null ? location.longitude() : null,
            imageStorageKey,
            obs.observedAt
        ));

        return obs;
    }

    public UUID getUserId() { return userId; }
    public String getSpeciesName() { return speciesName; }
    public String getScientificName() { return scientificName; }
    public double getTopConfidence() { return topConfidence; }
    public String getImageStorageKey() { return imageStorageKey; }
    public GpsCoordinates getLocation() { return location; }
    public ExifMetadata getExifMetadata() { return exifMetadata; }
    public String getNotes() { return notes; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
