package com.omyfish.observation.domain.port.in;

import com.omyfish.observation.domain.model.Observation;
import java.util.UUID;

public interface CreateObservationUseCase {
    record CreateCommand(
        UUID userId,
        String speciesName,
        String scientificName,
        double topConfidence,
        String imageStorageKey,
        Double latitude,
        Double longitude,
        String notes
    ) {}
    Observation create(CreateCommand command);
}
