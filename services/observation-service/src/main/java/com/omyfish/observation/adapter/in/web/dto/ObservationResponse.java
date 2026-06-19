package com.omyfish.observation.adapter.in.web.dto;

import com.omyfish.observation.domain.model.Observation;
import java.time.Instant;
import java.util.UUID;

public record ObservationResponse(
    UUID id,
    UUID userId,
    String speciesName,
    String scientificName,
    double topConfidence,
    String imageStorageKey,
    Double latitude,
    Double longitude,
    String notes,
    Instant observedAt,
    Instant createdAt
) {
    public static ObservationResponse from(Observation o) {
        Double lat = null, lon = null;
        if (o.getLocation() != null && o.getLocation().isPresent()) {
            lat = o.getLocation().latitude();
            lon = o.getLocation().longitude();
        }
        return new ObservationResponse(
            o.getId(), o.getUserId(), o.getSpeciesName(), o.getScientificName(),
            o.getTopConfidence(), o.getImageStorageKey(),
            lat, lon, o.getNotes(), o.getObservedAt(), o.getCreatedAt()
        );
    }
}
