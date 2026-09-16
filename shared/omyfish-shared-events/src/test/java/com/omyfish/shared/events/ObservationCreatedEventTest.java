package com.omyfish.shared.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationCreatedEventTest {

    @Test
    void convenienceConstructorGeneratesEnvelopeMetadata() {
        UUID observationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-05-01T10:15:30Z");
        Instant before = Instant.now();

        ObservationCreatedEvent event = new ObservationCreatedEvent(
            observationId, userId, "Atlantic Salmon", 46.81, -71.21, "img.jpg", observedAt);

        assertThat(UUID.fromString(event.eventId())).isNotNull();
        assertThat(event.occurredOn()).isBetween(before, Instant.now());
        assertThat(event.observationId()).isEqualTo(observationId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.speciesName()).isEqualTo("Atlantic Salmon");
        assertThat(event.latitude()).isEqualTo(46.81);
        assertThat(event.longitude()).isEqualTo(-71.21);
        assertThat(event.imageStorageKey()).isEqualTo("img.jpg");
        assertThat(event.observedAt()).isEqualTo(observedAt);
    }

    @Test
    void locationlessObservationKeepsNullCoordinates() {
        UUID id = UUID.randomUUID();

        ObservationCreatedEvent event = new ObservationCreatedEvent(
            id, id, "Northern Pike", null, null, "img.jpg", Instant.now());

        assertThat(event.latitude()).isNull();
        assertThat(event.longitude()).isNull();
    }

    @Test
    void routingMetadataMatchesTheObservationsExchange() {
        assertThat(ObservationCreatedEvent.EXCHANGE).isEqualTo("omyfish.observations");
        assertThat(ObservationCreatedEvent.ROUTING_KEY).isEqualTo("observation.created");
    }
}
