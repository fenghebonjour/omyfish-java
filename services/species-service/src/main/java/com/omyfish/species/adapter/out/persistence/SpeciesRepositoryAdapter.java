package com.omyfish.species.adapter.out.persistence;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SpeciesRepositoryAdapter implements SpeciesRepository {

    private final SpeciesJpaRepository jpaRepository;

    public SpeciesRepositoryAdapter(SpeciesJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Species> findByScientificName(String scientificName) {
        return jpaRepository.findByScientificName(scientificName).map(SpeciesJpaEntity::toDomain);
    }

    @Override
    public Species save(Species species) {
        return jpaRepository.save(SpeciesJpaEntity.from(species)).toDomain();
    }

    @Override
    public Optional<Species> findById(UUID id) {
        return jpaRepository.findById(id).map(SpeciesJpaEntity::toDomain);
    }

    @Override
    public List<Species> findAll() {
        return jpaRepository.findAll().stream().map(SpeciesJpaEntity::toDomain).toList();
    }
}
