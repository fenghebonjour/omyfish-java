package com.omyfish.shared.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishIdentifiedEventTest {

    private static final List<FishIdentifiedEvent.PredictionResult> PREDICTIONS = List.of(
        new FishIdentifiedEvent.PredictionResult("Atlantic Salmon", 0.91, 1),
        new FishIdentifiedEvent.PredictionResult("Brown Trout", 0.06, 2)
    );

    @Test
    void convenienceConstructorGeneratesEnvelopeMetadata() {
        UUID predictionId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        FishIdentifiedEvent event = new FishIdentifiedEvent(
            predictionId, observationId, userId, "Atlantic Salmon", 0.91, PREDICTIONS, "img.jpg");

        assertThat(UUID.fromString(event.eventId())).isNotNull();
        assertThat(event.occurredOn()).isBetween(before, Instant.now());
        assertThat(event.predictionId()).isEqualTo(predictionId);
        assertThat(event.observationId()).isEqualTo(observationId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.topSpeciesName()).isEqualTo("Atlantic Salmon");
        assertThat(event.topConfidence()).isEqualTo(0.91);
        assertThat(event.predictions()).isEqualTo(PREDICTIONS);
        assertThat(event.imageStorageKey()).isEqualTo("img.jpg");
    }

    @Test
    void eachEventGetsItsOwnEventId() {
        UUID id = UUID.randomUUID();
        FishIdentifiedEvent first = new FishIdentifiedEvent(id, id, id, "Pike", 0.5, PREDICTIONS, "img.jpg");
        FishIdentifiedEvent second = new FishIdentifiedEvent(id, id, id, "Pike", 0.5, PREDICTIONS, "img.jpg");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    void routingMetadataMatchesTheSpeciesExchange() {
        assertThat(FishIdentifiedEvent.EXCHANGE).isEqualTo("omyfish.species");
        assertThat(FishIdentifiedEvent.ROUTING_KEY).isEqualTo("fish.identified");
    }

    @Test
    void canonicalConstructorKeepsSuppliedEnvelope() {
        Instant occurredOn = Instant.parse("2026-01-02T03:04:05Z");
        UUID id = UUID.randomUUID();

        FishIdentifiedEvent event = new FishIdentifiedEvent(
            "event-1", occurredOn, id, id, id, "Walleye", 0.77, PREDICTIONS, "img.jpg");

        assertThat(event.eventId()).isEqualTo("event-1");
        assertThat(event.occurredOn()).isEqualTo(occurredOn);
    }
}
