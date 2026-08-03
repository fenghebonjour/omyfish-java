package com.omyfish.species.domain.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishIdentifiedEventTest {

    @Test
    void carriesIdentificationPayloadAndDomainEventMetadata() {
        UUID predictionId = UUID.randomUUID();
        Instant before = Instant.now();

        FishIdentifiedEvent event = new FishIdentifiedEvent(predictionId, "Atlantic Salmon", 0.91);

        assertThat(event.getPredictionId()).isEqualTo(predictionId);
        assertThat(event.getSpeciesName()).isEqualTo("Atlantic Salmon");
        assertThat(event.getConfidence()).isEqualTo(0.91);
        assertThat(event.getEventType()).isEqualTo("fish.identified");
        assertThat(UUID.fromString(event.getEventId())).isNotNull();
        assertThat(event.getOccurredOn()).isBetween(before, Instant.now());
    }
}
