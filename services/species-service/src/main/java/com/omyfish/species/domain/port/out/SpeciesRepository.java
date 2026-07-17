package com.omyfish.species.domain.port.out;

import com.omyfish.species.domain.model.Species;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository {
    Optional<Species> findByScientificName(String scientificName);
    Species save(Species species);
    Optional<Species> findById(UUID id);
    List<Species> findAll();
}
