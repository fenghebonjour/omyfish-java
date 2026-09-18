package com.omyfish.observation.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import org.springframework.stereotype.Component;

/**
 * Writes the event to the same database transaction as the aggregate save instead of publishing
 * to RabbitMQ directly, so a crash between the two can never lose the event (WEAKNESS_AUDIT.md
 * §2.3). {@link com.omyfish.observation.adapter.out.messaging.OutboxPublisherJob} polls this
 * table separately and does the actual RabbitMQ send.
 */
@Component
public class OutboxEventPublisher implements EventPublisherPort {

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ObservationCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEventJpaEntity(
                event.getEventType(),
                "omyfish.observations",
                "observation.created",
                payload
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + event.getEventType() + " for outbox", e);
        }
    }
}
