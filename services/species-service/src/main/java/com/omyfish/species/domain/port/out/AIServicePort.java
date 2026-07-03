package com.omyfish.species.domain.port.out;

import java.util.List;

public interface AIServicePort {

    List<AIPrediction> predict(String imageBase64, int topK);

    record AIPrediction(
        String scientificName,
        String commonName,
        double confidence,
        int rank,
        String conservationStatus,
        String habitat,
        String diet,
        Integer maxSizeCm,
        String description,
        String funFact
    ) {}
}
