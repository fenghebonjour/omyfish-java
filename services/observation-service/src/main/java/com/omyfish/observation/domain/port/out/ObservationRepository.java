package com.omyfish.observation.domain.port.out;

import com.omyfish.observation.domain.model.Observation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservationRepository {
    Observation save(Observation observation);
    Optional<Observation> findById(UUID id);
    List<Observation> findByUserId(UUID userId);
    List<Observation> findAllWithLocation();
    void deleteById(UUID id);
}
