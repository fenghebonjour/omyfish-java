package com.omyfish.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRootTest {

    private static class CaughtEvent extends DomainEvent {
        CaughtEvent() { super("fish.caught"); }
    }

    private static class Catch extends AggregateRoot<UUID> {
        Catch() { super(UUID.randomUUID()); }
        void record(DomainEvent event) { registerEvent(event); }
    }

    @Test
    void pullReturnsRegisteredEventsInOrder() {
        Catch aggregate = new Catch();
        CaughtEvent first = new CaughtEvent();
        CaughtEvent second = new CaughtEvent();
        aggregate.record(first);
        aggregate.record(second);

        assertThat(aggregate.pullDomainEvents()).containsExactly(first, second);
    }

    @Test
    void pullDrainsTheEventList() {
        Catch aggregate = new Catch();
        aggregate.record(new CaughtEvent());

        assertThat(aggregate.pullDomainEvents()).hasSize(1);
        assertThat(aggregate.pullDomainEvents()).isEmpty();
    }

    @Test
    void pulledEventsAreImmutable() {
        Catch aggregate = new Catch();
        aggregate.record(new CaughtEvent());

        List<DomainEvent> events = aggregate.pullDomainEvents();
        assertThatThrownBy(() -> events.add(new CaughtEvent()))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
