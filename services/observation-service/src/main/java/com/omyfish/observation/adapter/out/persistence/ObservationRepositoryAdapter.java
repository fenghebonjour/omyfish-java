package com.omyfish.observation.adapter.out.persistence;

import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ObservationRepositoryAdapter implements ObservationRepository {

    private final ObservationJpaRepository jpaRepository;

    public ObservationRepositoryAdapter(ObservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Observation save(Observation observation) {
        return jpaRepository.save(ObservationJpaEntity.from(observation)).toDomain();
    }

    @Override
    public Optional<Observation> findById(UUID id) {
        return jpaRepository.findById(id).map(ObservationJpaEntity::toDomain);
    }

    @Override
    public List<Observation> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
            .map(ObservationJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Observation> findAllWithLocation() {
        return jpaRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull().stream()
            .map(ObservationJpaEntity::toDomain)
            .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
