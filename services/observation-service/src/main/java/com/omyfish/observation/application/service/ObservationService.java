package com.omyfish.observation.application.service;

import com.omyfish.observation.adapter.in.web.dto.CreateObservationRequest;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.out.ObservationRepository;

public class ObservationService implements CreateObservationUseCase {

    private final ObservationRepository repository;

    public ObservationService(ObservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Observation create(CreateObservationRequest request) {
        GpsCoordinates location = (request.latitude() != null && request.longitude() != null)
            ? GpsCoordinates.of(request.latitude(), request.longitude())
            : GpsCoordinates.unknown();

        Observation observation = Observation.create(
            request.userId(),
            request.speciesName(),
            request.scientificName(),
            request.topConfidence(),
            request.imageStorageKey(),
            location,
            null,
            request.notes()
        );

        return repository.save(observation);
    }
}
