package com.omyfish.species.domain.port.out;

import java.util.List;

public interface AIServicePort {

    List<AIPrediction> predict(String imageStorageKey, int topK);

    record AIPrediction(
        String scientificName,
        String commonName,
        double confidence,
        int rank
    ) {}
}
