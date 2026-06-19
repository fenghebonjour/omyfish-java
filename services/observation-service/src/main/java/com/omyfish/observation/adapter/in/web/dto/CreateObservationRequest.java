package com.omyfish.observation.adapter.in.web.dto;

public record CreateObservationRequest(
    String speciesName,
    String scientificName,
    double topConfidence,
    String imageStorageKey,
    Double latitude,
    Double longitude,
    String notes
) {}
