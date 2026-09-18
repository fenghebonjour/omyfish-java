package com.omyfish.observation.adapter.out.messaging;

import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaEntity;
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table {@code OutboxEventPublisher} writes to (in the same transaction as the
 * aggregate save) and does the actual RabbitMQ send, marking each row published once it succeeds
 * (WEAKNESS_AUDIT.md §2.3). A row can be sent twice if the process crashes between the send and
 * the commit that marks it published — acceptable at-least-once delivery, since
 * {@code ObservationCreatedConsumer} already dedups by event id (§2.4).
 *
 * <p>Single instance only, same assumption the §1.2 rate limiter makes — a second replica would
 * double-publish every row since there's no claim/lock step here.
 */
@Component
public class OutboxPublisherJob {

    private final OutboxEventJpaRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherJob(OutboxEventJpaRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<OutboxEventJpaEntity> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEventJpaEntity event : pending) {
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props);
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message);
            event.markPublished(Instant.now());
        }
    }
}
