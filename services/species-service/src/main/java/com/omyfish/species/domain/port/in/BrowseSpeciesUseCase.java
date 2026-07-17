package com.omyfish.species.domain.port.in;

import com.omyfish.species.domain.model.Species;
import java.util.List;
import java.util.Optional;

public interface BrowseSpeciesUseCase {
    /** Lists the species catalog, optionally filtered by the NA-freshwater flag. */
    List<Species> list(Boolean northAmericanFreshwater);

    Optional<Species> byScientificName(String scientificName);
}
