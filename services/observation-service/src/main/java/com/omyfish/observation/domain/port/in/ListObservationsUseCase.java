package com.omyfish.observation.domain.port.in;

import com.omyfish.observation.domain.model.Observation;
import java.util.List;
import java.util.UUID;

public interface ListObservationsUseCase {
    List<Observation> listByUser(UUID userId);
}
