package com.omyfish.observation.domain.port.in;

import com.omyfish.observation.domain.model.Observation;
import java.util.UUID;

public interface GetObservationUseCase {
    Observation get(UUID id);
}
