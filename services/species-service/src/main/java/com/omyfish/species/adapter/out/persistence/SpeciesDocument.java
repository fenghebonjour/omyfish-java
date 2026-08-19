package com.omyfish.species.adapter.out.persistence;

import com.omyfish.species.domain.model.Species;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "species")
class SpeciesDocument {

    @Id
    private UUID id;
    private String scientificName;
    private String commonName;
    private String family;
    private String conservationStatus;
    private String habitat;
    private String geographicRange;
    private String description;
    private boolean northAmericanFreshwater;

    private SpeciesDocument() {}

    UUID getId() { return id; }

    static SpeciesDocument from(Species s) {
        SpeciesDocument d = new SpeciesDocument();
        d.id = s.getId();
        d.scientificName = s.getScientificName();
        d.commonName = s.getCommonName();
        d.family = s.getFamily();
        d.conservationStatus = s.getConservationStatus();
        d.habitat = s.getHabitat();
        d.geographicRange = s.getGeographicRange();
        d.description = s.getDescription();
        d.northAmericanFreshwater = s.isNorthAmericanFreshwater();
        return d;
    }

    Species toDomain() {
        return Species.reconstitute(
            id, scientificName, commonName, family, conservationStatus,
            habitat, geographicRange, description, null, null, null, northAmericanFreshwater
        );
    }
}
