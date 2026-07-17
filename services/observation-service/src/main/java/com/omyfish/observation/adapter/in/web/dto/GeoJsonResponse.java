package com.omyfish.observation.adapter.in.web.dto;

import com.omyfish.observation.domain.model.Observation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GeoJSON FeatureCollection — property names mirror the dotnet stack's endpoint. */
public record GeoJsonResponse(String type, List<Feature> features) {

    public record Feature(String type, Geometry geometry, Properties properties) {}

    public record Geometry(String type, double[] coordinates) {}

    public record Properties(
        UUID id,
        String speciesName,
        String scientificName,
        double confidence,
        String notes,
        Instant observedAt
    ) {}

    public static GeoJsonResponse from(List<Observation> observations) {
        List<Feature> features = observations.stream()
            .map(o -> new Feature(
                "Feature",
                new Geometry("Point", new double[] {
                    o.getLocation().longitude(), o.getLocation().latitude()
                }),
                new Properties(
                    o.getId(), o.getSpeciesName(), o.getScientificName(),
                    o.getTopConfidence(), o.getNotes(), o.getObservedAt()
                )
            ))
            .toList();
        return new GeoJsonResponse("FeatureCollection", features);
    }
}
