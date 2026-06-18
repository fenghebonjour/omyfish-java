package com.omyfish.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishIdentifiedEvent(
    String eventId,
    Instant occurredOn,
    UUID predictionId,
    UUID observationId,
    UUID userId,
    String topSpeciesName,
    double topConfidence,
    List<PredictionResult> predictions,
    String imageStorageKey
) {
    public FishIdentifiedEvent(
        UUID predictionId,
        UUID observationId,
        UUID userId,
        String topSpeciesName,
        double topConfidence,
        List<PredictionResult> predictions,
        String imageStorageKey
    ) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            predictionId,
            observationId,
            userId,
            topSpeciesName,
            topConfidence,
            predictions,
            imageStorageKey
        );
    }

    public record PredictionResult(String speciesName, double confidence, int rank) {}

    public static final String ROUTING_KEY = "fish.identified";
    public static final String EXCHANGE = "omyfish.species";
}
