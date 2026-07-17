package com.omyfish.species.application.service;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.in.BrowseSpeciesUseCase;
import com.omyfish.species.domain.port.out.SpeciesRepository;

import java.util.List;
import java.util.Optional;

public class SpeciesCatalogService implements BrowseSpeciesUseCase {

    private final SpeciesRepository speciesRepository;

    public SpeciesCatalogService(SpeciesRepository speciesRepository) {
        this.speciesRepository = speciesRepository;
    }

    @Override
    public List<Species> list(Boolean northAmericanFreshwater) {
        List<Species> all = speciesRepository.findAll();
        if (northAmericanFreshwater == null) {
            return all;
        }
        return all.stream()
            .filter(s -> s.isNorthAmericanFreshwater() == northAmericanFreshwater)
            .toList();
    }

    @Override
    public Optional<Species> byScientificName(String scientificName) {
        return speciesRepository.findByScientificName(scientificName);
    }
}
