package com.omyfish.observation.adapter.out.persistence;

import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "observations", schema = "observation")
class ObservationJpaEntity {

    @Id
    private UUID id;
    private UUID userId;
    private String speciesName;
    private String scientificName;
    private double topConfidence;
    private String imageStorageKey;
    private Double latitude;
    private Double longitude;
    private String notes;
    private Instant observedAt;
    private Instant createdAt;

    protected ObservationJpaEntity() {}

    static ObservationJpaEntity from(Observation o) {
        ObservationJpaEntity e = new ObservationJpaEntity();
        e.id = o.getId();
        e.userId = o.getUserId();
        e.speciesName = o.getSpeciesName();
        e.scientificName = o.getScientificName();
        e.topConfidence = o.getTopConfidence();
        e.imageStorageKey = o.getImageStorageKey();
        if (o.getLocation() != null && o.getLocation().isPresent()) {
            e.latitude = o.getLocation().latitude();
            e.longitude = o.getLocation().longitude();
        }
        e.notes = o.getNotes();
        e.observedAt = o.getObservedAt();
        e.createdAt = o.getCreatedAt();
        return e;
    }

    Observation toDomain() {
        GpsCoordinates coords = (latitude != null && longitude != null)
            ? GpsCoordinates.of(latitude, longitude)
            : GpsCoordinates.unknown();
        return Observation.reconstitute(
            id, userId, speciesName, scientificName, topConfidence,
            imageStorageKey, coords, null, notes, observedAt, createdAt
        );
    }
}
