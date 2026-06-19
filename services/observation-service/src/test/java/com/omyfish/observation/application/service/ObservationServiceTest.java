package com.omyfish.observation.application.service;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.exception.ObservationNotFoundException;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase.CreateCommand;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObservationServiceTest {

    @Mock ObservationRepository repository;
    @Mock EventPublisherPort eventPublisher;
    @InjectMocks ObservationService service;

    @Test
    void create_savesObservation_publishesEvent() {
        UUID userId = UUID.randomUUID();
        CreateCommand cmd = new CreateCommand(userId, "Salmon", "Salmo salar",
            0.95, "img.jpg", null, null, null);

        Observation saved = Observation.create(userId, "Salmon", "Salmo salar",
            0.95, "img.jpg", GpsCoordinates.unknown(), null, null);
        when(repository.save(any())).thenReturn(saved);

        Observation result = service.create(cmd);

        assertThat(result.getSpeciesName()).isEqualTo("Salmon");
        verify(repository).save(any());
        ArgumentCaptor<ObservationCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(ObservationCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSpeciesName()).isEqualTo("Salmon");
    }

    @Test
    void create_withGpsCoordinates_setsLocation() {
        UUID userId = UUID.randomUUID();
        CreateCommand cmd = new CreateCommand(userId, "Trout", "Salmo trutta",
            0.80, "img.jpg", 51.5, -0.1, null);

        Observation saved = Observation.create(userId, "Trout", "Salmo trutta",
            0.80, "img.jpg", GpsCoordinates.of(51.5, -0.1), null, null);
        when(repository.save(any())).thenReturn(saved);

        Observation result = service.create(cmd);

        assertThat(result.getLocation().isPresent()).isTrue();
        assertThat(result.getLocation().latitude()).isEqualTo(51.5);
    }

    @Test
    void get_notFound_throwsObservationNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ObservationNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void listByUser_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        Observation obs = Observation.create(userId, "Trout", "Salmo trutta",
            0.80, "img.jpg", GpsCoordinates.unknown(), null, null);
        when(repository.findByUserId(userId)).thenReturn(List.of(obs));

        List<Observation> result = service.listByUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpeciesName()).isEqualTo("Trout");
        verify(repository).findByUserId(userId);
    }
}
