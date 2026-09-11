package com.omyfish.species.domain.port.out;

import com.omyfish.species.domain.model.Species;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository {
    Optional<Species> findByScientificName(String scientificName);
    // Batched lookup for identify's per-prediction loop (BACKLOG.md item G, WEAKNESS_AUDIT.md
    // §3.4) — avoids one DB round-trip per AI prediction.
    List<Species> findByScientificNames(Collection<String> scientificNames);
    Species save(Species species);
    Optional<Species> findById(UUID id);
    List<Species> findAll();
}
