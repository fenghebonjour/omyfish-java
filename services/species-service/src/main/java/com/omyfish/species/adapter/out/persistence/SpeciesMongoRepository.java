package com.omyfish.species.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

interface SpeciesMongoRepository extends MongoRepository<SpeciesDocument, UUID> {
    Optional<SpeciesDocument> findByScientificName(String scientificName);
}
