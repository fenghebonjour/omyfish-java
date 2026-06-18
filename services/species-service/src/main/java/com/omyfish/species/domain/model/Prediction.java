package com.omyfish.species.domain.model;

import com.omyfish.shared.domain.Entity;
import com.omyfish.species.domain.model.valueobject.ConfidenceScore;

import java.time.Instant;
import java.util.UUID;

public class Prediction extends Entity<UUID> {

    private final Species species;
    private final String imageStorageKey;
    private final ConfidenceScore confidence;
    private final int rank;
    private final Instant predictedAt;

    private Prediction(Species species, String imageStorageKey, ConfidenceScore confidence, int rank) {
        super(UUID.randomUUID());
        this.species = species;
        this.imageStorageKey = imageStorageKey;
        this.confidence = confidence;
        this.rank = rank;
        this.predictedAt = Instant.now();
    }

    static Prediction create(Species species, String imageStorageKey, ConfidenceScore confidence) {
        return new Prediction(species, imageStorageKey, confidence, 1);
    }

    public static Prediction createRanked(Species species, String imageStorageKey, ConfidenceScore confidence, int rank) {
        return new Prediction(species, imageStorageKey, confidence, rank);
    }

    public Species getSpecies() { return species; }
    public String getImageStorageKey() { return imageStorageKey; }
    public ConfidenceScore getConfidence() { return confidence; }
    public int getRank() { return rank; }
    public Instant getPredictedAt() { return predictedAt; }
}
