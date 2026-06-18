package com.omyfish.species.domain.port.out;

import com.omyfish.species.domain.model.Prediction;
import java.util.List;
import java.util.UUID;

public interface EventPublisherPort {
    void publishFishIdentified(
        UUID predictionId,
        UUID observationId,
        UUID userId,
        String topSpeciesName,
        double topConfidence,
        List<Prediction> predictions,
        String imageStorageKey
    );
}
