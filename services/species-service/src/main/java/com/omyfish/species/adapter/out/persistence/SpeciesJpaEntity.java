package com.omyfish.species.adapter.out.persistence;

import com.omyfish.species.domain.model.Species;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "species", schema = "species")
class SpeciesJpaEntity {

    @Id
    private UUID id;
    private String scientificName;
    private String commonName;
    private String family;
    private String conservationStatus;
    private String habitat;
    private String geographicRange;
    private String description;
    @Column(name = "is_north_american_freshwater")
    private boolean northAmericanFreshwater;

    protected SpeciesJpaEntity() {}

    UUID getId() { return id; }
    String getScientificName() { return scientificName; }
    String getCommonName() { return commonName; }
    String getFamily() { return family; }
    String getConservationStatus() { return conservationStatus; }
    String getHabitat() { return habitat; }
    String getGeographicRange() { return geographicRange; }
    String getDescription() { return description; }
    boolean isNorthAmericanFreshwater() { return northAmericanFreshwater; }

    static SpeciesJpaEntity from(Species s) {
        SpeciesJpaEntity e = new SpeciesJpaEntity();
        e.id = s.getId();
        e.scientificName = s.getScientificName();
        e.commonName = s.getCommonName();
        e.family = s.getFamily();
        e.conservationStatus = s.getConservationStatus();
        e.habitat = s.getHabitat();
        e.geographicRange = s.getGeographicRange();
        e.description = s.getDescription();
        e.northAmericanFreshwater = s.isNorthAmericanFreshwater();
        return e;
    }

    Species toDomain() {
        return Species.create(
            scientificName, commonName, family, conservationStatus,
            habitat, geographicRange, description, northAmericanFreshwater
        );
    }
}
