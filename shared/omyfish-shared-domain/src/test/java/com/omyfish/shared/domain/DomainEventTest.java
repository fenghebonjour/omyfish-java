package com.omyfish.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTest {

    private static class IdentifiedEvent extends DomainEvent {
        IdentifiedEvent() { super("fish.identified"); }
    }

    @Test
    void capturesEventTypeAndMetadata() {
        Instant before = Instant.now();
        IdentifiedEvent event = new IdentifiedEvent();

        assertThat(event.getEventType()).isEqualTo("fish.identified");
        assertThat(UUID.fromString(event.getEventId())).isNotNull();
        assertThat(event.getOccurredOn()).isBetween(before, Instant.now());
    }

    @Test
    void eachEventGetsItsOwnId() {
        assertThat(new IdentifiedEvent().getEventId()).isNotEqualTo(new IdentifiedEvent().getEventId());
    }
}
