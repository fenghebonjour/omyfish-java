package com.omyfish.species.domain.event;

import com.omyfish.shared.domain.DomainEvent;
import java.util.UUID;

public class FishIdentifiedEvent extends DomainEvent {

    private final UUID predictionId;
    private final String speciesName;
    private final double confidence;

    public FishIdentifiedEvent(UUID predictionId, String speciesName, double confidence) {
        super("fish.identified");
        this.predictionId = predictionId;
        this.speciesName = speciesName;
        this.confidence = confidence;
    }

    public UUID getPredictionId() { return predictionId; }
    public String getSpeciesName() { return speciesName; }
    public double getConfidence() { return confidence; }
}
