package com.omyfish.observation.application.service;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.exception.ObservationNotFoundException;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.GetObservationUseCase;
import com.omyfish.observation.domain.port.in.ListObservationsUseCase;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import com.omyfish.shared.domain.DomainEvent;

import java.util.List;
import java.util.UUID;

public class ObservationService implements CreateObservationUseCase, GetObservationUseCase, ListObservationsUseCase {

    private final ObservationRepository repository;
    private final EventPublisherPort eventPublisher;

    public ObservationService(ObservationRepository repository, EventPublisherPort eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Observation create(CreateCommand command) {
        GpsCoordinates location = (command.latitude() != null && command.longitude() != null)
            ? GpsCoordinates.of(command.latitude(), command.longitude())
            : GpsCoordinates.unknown();

        Observation observation = Observation.create(
            command.userId(),
            command.speciesName(),
            command.scientificName(),
            command.topConfidence(),
            command.imageStorageKey(),
            location,
            null,
            command.notes()
        );

        Observation saved = repository.save(observation);

        for (DomainEvent e : observation.pullDomainEvents()) {
            if (e instanceof ObservationCreatedEvent oce) {
                eventPublisher.publish(oce);
            }
        }

        return saved;
    }

    @Override
    public Observation get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ObservationNotFoundException(id));
    }

    @Override
    public List<Observation> listByUser(UUID userId) {
        return repository.findByUserId(userId);
    }
}
