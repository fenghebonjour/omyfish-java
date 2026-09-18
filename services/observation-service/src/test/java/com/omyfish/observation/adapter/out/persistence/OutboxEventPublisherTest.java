package com.omyfish.observation.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock OutboxEventJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void publish_writesOutboxRowWithSerializedPayload_insteadOfCallingRabbitDirectly() {
        OutboxEventPublisher publisher = new OutboxEventPublisher(outboxRepository, objectMapper);
        ObservationCreatedEvent event = new ObservationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "Walleye", 45.5, -73.5, "key.jpg", Instant.now());

        publisher.publish(event);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();
        assertThat(saved.getExchange()).isEqualTo("omyfish.observations");
        assertThat(saved.getRoutingKey()).isEqualTo("observation.created");
        assertThat(saved.getPayload()).contains("Walleye").contains(event.getObservationId().toString());
        assertThat(saved.getPublishedAt()).isNull();
    }
}
