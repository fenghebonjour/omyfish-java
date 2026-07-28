package com.omyfish.species.domain.port.out;

import java.time.LocalDate;
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

    /** Quebec fishing zone catch/length limits for the given coordinates. */
    RegsLimits getRegsLimits(double lat, double lon, String species);

    /** Raw GeoJSON FeatureCollection of the 34 Quebec fishing zone polygons — passed through untouched. */
    Map<String, Object> getRegsZonesGeoJson();

    /** Nearest consumption-advisory sampling stations to the given coordinates. */
    List<RegsStation> getRegsConsumptionStations(double lat, double lon, int limit);

    /** Mercury/contaminant consumption advisory (meals/month) for a species + fish size near the given coordinates. */
    RegsConsumption getRegsConsumption(double lat, double lon, String species, Double sizeCm);

    /** Free-form Quebec fishing regs/tackle Q&A, backed by omyfish-ai's RAG chatbot. */
    RegsAnswer askRegs(String question);

    record RegsSpeciesLimit(
        String species, String period, String catchLimit,
        String lengthLimit, String fishingDevice, String note) {}

    record RegsLimits(
        double lat, double lon, String zoneName, String zoneInfoUrl,
        List<RegsSpeciesLimit> rules, String disclaimer) {}

    record RegsStation(
        String noBqma, String hydronyme, double latitude, double longitude, double distanceKm) {}

    record RegsConsumption(
        double lat, double lon, String species, String stationName, double distanceKm,
        String sizeClass, Integer mealsPerMonth, String fishingStatus, String note, String disclaimer) {}

    record RegsAnswer(String question, String answer, List<String> sources, String disclaimer) {}

    record BiteForecast(
        String species,
        double lat,
        double lon,
        List<BiteHourlyScore> hourly,
        List<BiteHourlyScore> bestWindows,
        List<TimeWindow> majorWindows,
        List<TimeWindow> minorWindows,
        List<SunTimes> sunTimes,
        CurrentConditions current
    ) {}

    record TimeWindow(LocalDateTime start, LocalDateTime end) {}

    record SunTimes(LocalDate date, LocalDateTime sunrise, LocalDateTime sunset) {}

    /** Live nowcast — null when the provider omits it. */
    record CurrentConditions(
        LocalDateTime time, double precipitationMm, boolean isStorm, boolean isHeavyPrecip) {}

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
