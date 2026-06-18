package com.omyfish.species.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpeciesJpaRepository extends JpaRepository<SpeciesJpaEntity, UUID> {
    Optional<SpeciesJpaEntity> findByScientificName(String scientificName);
}
