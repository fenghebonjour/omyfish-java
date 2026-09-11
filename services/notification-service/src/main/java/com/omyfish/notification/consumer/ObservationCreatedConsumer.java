package com.omyfish.notification.consumer;

import com.omyfish.notification.model.Notification;
import com.omyfish.notification.repository.NotificationRepository;
import com.omyfish.shared.events.ObservationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ObservationCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ObservationCreatedConsumer.class);

    private final NotificationRepository repository;

    public ObservationCreatedConsumer(NotificationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = "${omyfish.rabbitmq.queues.observation-created}")
    public void handle(ObservationCreatedEvent event) {
        log.info("Observation created: id={} species={} user={}",
            event.observationId(), event.speciesName(), event.userId());

        // Skips on a redelivered event instead of inserting a duplicate notification — RabbitMQ
        // redelivery under at-least-once + retry will eventually happen
        // (BACKLOG.md item G, WEAKNESS_AUDIT.md §2.4).
        if (repository.existsBySourceEventId(event.eventId())) {
            log.info("Duplicate delivery for event {} — already processed, skipping", event.eventId());
            return;
        }

        Notification notification = new Notification(
            event.userId(),
            "OBSERVATION_CREATED",
            "Fish identified: " + event.speciesName(),
            "Your observation of " + event.speciesName() + " has been recorded.",
            event.eventId()
        );
        repository.save(notification);
        log.info("Notification persisted: id={} userId={}", notification.getId(), notification.getUserId());
    }
}
