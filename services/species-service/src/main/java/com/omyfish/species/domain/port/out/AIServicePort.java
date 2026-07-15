package com.omyfish.species.domain.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AIServicePort {

    AIResult predict(String imageBase64, int topK);

    /**
     * Species accepts a profile key or any common/scientific name (e.g. from
     * a confirmed fish ID) — the adapter resolves it, general fallback.
     * The six-factor breakdown is a product invariant: pass it through to
     * clients untouched, never reduce a forecast to the headline score.
     */
    BiteForecast getBiteForecast(double lat, double lon, String species, int hours);

    record BiteForecast(
        String species,
        double lat,
        double lon,
        List<BiteHourlyScore> hourly,
        List<BiteHourlyScore> bestWindows
    ) {}

    record BiteHourlyScore(
        LocalDateTime timestamp,
        double score,
        Map<String, Double> breakdown,
        Map<String, Double> weightedContribution,
        double timeOfDayMultiplier,
        String safetyFlag
    ) {}

    record AIResult(List<AIPrediction> predictions, boolean isFish) {}

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
