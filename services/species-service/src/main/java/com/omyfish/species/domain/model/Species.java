package com.omyfish.species.domain.model;

import com.omyfish.shared.domain.AggregateRoot;
import com.omyfish.species.domain.model.valueobject.ConfidenceScore;

import java.util.UUID;

public class Species extends AggregateRoot<UUID> {

    private String scientificName;
    private String commonName;
    private String family;
    private String conservationStatus;
    private String habitat;
    private String geographicRange;
    private String description;
    private boolean isNorthAmericanFreshwater;

    private Species() {
        super(UUID.randomUUID());
    }

    public static Species create(
        String scientificName,
        String commonName,
        String family,
        String conservationStatus,
        String habitat,
        String geographicRange,
        String description,
        boolean isNorthAmericanFreshwater
    ) {
        Species species = new Species();
        species.scientificName = scientificName;
        species.commonName = commonName;
        species.family = family;
        species.conservationStatus = conservationStatus;
        species.habitat = habitat;
        species.geographicRange = geographicRange;
        species.description = description;
        species.isNorthAmericanFreshwater = isNorthAmericanFreshwater;
        return species;
    }

    public Prediction identifyFrom(String imageStorageKey, ConfidenceScore confidence) {
        return Prediction.create(this, imageStorageKey, confidence);
    }

    public String getScientificName() { return scientificName; }
    public String getCommonName() { return commonName; }
    public String getFamily() { return family; }
    public String getConservationStatus() { return conservationStatus; }
    public String getHabitat() { return habitat; }
    public String getGeographicRange() { return geographicRange; }
    public String getDescription() { return description; }
    public boolean isNorthAmericanFreshwater() { return isNorthAmericanFreshwater; }
}
