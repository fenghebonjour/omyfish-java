package com.omyfish.observation.domain.port.out;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;

public interface EventPublisherPort {
    void publish(ObservationCreatedEvent event);
}
