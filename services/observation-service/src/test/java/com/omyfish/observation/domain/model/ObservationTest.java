package com.omyfish.observation.domain.model;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.shared.domain.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ObservationTest {

    @Test
    void create_assignsRandomUuid_registersObservationCreatedEvent() {
        UUID userId = UUID.randomUUID();

        Observation obs = Observation.create(userId, "Atlantic Salmon", "Salmo salar", 0.95,
            "fish-images/test.jpg", GpsCoordinates.unknown(), null, null);

        assertThat(obs.getId()).isNotNull();
        assertThat(obs.getUserId()).isEqualTo(userId);
        assertThat(obs.getSpeciesName()).isEqualTo("Atlantic Salmon");
        assertThat(obs.getTopConfidence()).isEqualTo(0.95);
        assertThat(obs.getCreatedAt()).isNotNull();

        List<DomainEvent> events = obs.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ObservationCreatedEvent.class);
        ObservationCreatedEvent event = (ObservationCreatedEvent) events.get(0);
        assertThat(event.getObservationId()).isEqualTo(obs.getId());
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getSpeciesName()).isEqualTo("Atlantic Salmon");
    }

    @Test
    void pullDomainEvents_clearsEventList() {
        Observation obs = Observation.create(UUID.randomUUID(), "Trout", "Salmo trutta",
            0.8, "key", GpsCoordinates.unknown(), null, null);

        obs.pullDomainEvents();

        assertThat(obs.pullDomainEvents()).isEmpty();
    }

    @Test
    void reconstitute_preservesUuid_registersNoEvents() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Observation obs = Observation.reconstitute(id, userId, "Brown Trout", "Salmo trutta",
            0.88, "key", GpsCoordinates.of(51.5, -0.1), null, "notes", now, now);

        assertThat(obs.getId()).isEqualTo(id);
        assertThat(obs.getNotes()).isEqualTo("notes");
        assertThat(obs.getLocation().isPresent()).isTrue();
        assertThat(obs.getLocation().latitude()).isEqualTo(51.5);
        assertThat(obs.pullDomainEvents()).isEmpty();
    }

    @Test
    void gpsCoordinates_outOfRangeLatitude_throws() {
        assertThatThrownBy(() -> GpsCoordinates.of(91.0, 0.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Latitude");
    }

    @Test
    void gpsCoordinates_outOfRangeLongitude_throws() {
        assertThatThrownBy(() -> GpsCoordinates.of(0.0, 181.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Longitude");
    }
}
