package com.omyfish.species.adapter.in.web.dto;

import java.util.List;

public record PredictionResponse(
    List<PredictionItem> predictions,
    boolean uncertain,
    String imageKey
) {
    public record PredictionItem(
        String speciesName,
        String scientificName,
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
