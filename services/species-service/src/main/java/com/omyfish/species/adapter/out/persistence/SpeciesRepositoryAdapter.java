package com.omyfish.species.adapter.out.persistence;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SpeciesRepositoryAdapter implements SpeciesRepository {

    private final SpeciesMongoRepository mongoRepository;

    public SpeciesRepositoryAdapter(SpeciesMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public Optional<Species> findByScientificName(String scientificName) {
        return mongoRepository.findByScientificName(scientificName).map(SpeciesDocument::toDomain);
    }

    @Override
    public Species save(Species species) {
        return mongoRepository.save(SpeciesDocument.from(species)).toDomain();
    }

    @Override
    public Optional<Species> findById(UUID id) {
        return mongoRepository.findById(id).map(SpeciesDocument::toDomain);
    }

    @Override
    public List<Species> findAll() {
        return mongoRepository.findAll().stream().map(SpeciesDocument::toDomain).toList();
    }
}
