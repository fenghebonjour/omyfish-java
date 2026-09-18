package com.omyfish.observation.adapter.out.messaging;

import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaEntity;
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock OutboxEventJpaRepository outboxRepository;
    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void publishPending_sendsEachRowToRabbitAndMarksItPublished() {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
            "observation.created", "omyfish.observations", "observation.created", "{\"speciesName\":\"Walleye\"}");
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));

        new OutboxPublisherJob(outboxRepository, rabbitTemplate).publishPending();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("omyfish.observations"), eq("observation.created"), messageCaptor.capture());
        assertThat(new String(messageCaptor.getValue().getBody())).contains("Walleye");
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPending_noPendingRows_doesNotCallRabbit() {
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        new OutboxPublisherJob(outboxRepository, rabbitTemplate).publishPending();

        verifyNoInteractions(rabbitTemplate);
    }
}
