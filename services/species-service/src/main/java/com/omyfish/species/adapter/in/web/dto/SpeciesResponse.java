package com.omyfish.species.adapter.in.web.dto;

import com.omyfish.species.domain.model.Species;

public record SpeciesResponse(
    String scientificName,
    String commonName,
    String family,
    String conservationStatus,
    String habitat,
    String geographicRange,
    String description,
    boolean northAmericanFreshwater
) {
    public static SpeciesResponse from(Species s) {
        return new SpeciesResponse(
            s.getScientificName(), s.getCommonName(), s.getFamily(),
            s.getConservationStatus(), s.getHabitat(), s.getGeographicRange(),
            s.getDescription(), s.isNorthAmericanFreshwater()
        );
    }
}
