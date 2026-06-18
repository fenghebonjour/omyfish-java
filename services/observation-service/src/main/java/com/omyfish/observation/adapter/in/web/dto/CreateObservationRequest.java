package com.omyfish.observation.adapter.in.web.dto;

import java.util.UUID;

public record CreateObservationRequest(
    UUID userId,
    String speciesName,
    String scientificName,
    double topConfidence,
    String imageStorageKey,
    Double latitude,
    Double longitude,
    String notes
) {}
