package com.omyfish.observation.domain.port.in;

import com.omyfish.observation.adapter.in.web.dto.CreateObservationRequest;
import com.omyfish.observation.domain.model.Observation;

public interface CreateObservationUseCase {
    Observation create(CreateObservationRequest request);
}
