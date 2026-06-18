package com.omyfish.notification.consumer;

import com.omyfish.shared.events.ObservationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ObservationCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ObservationCreatedConsumer.class);

    @RabbitListener(queues = "${omyfish.rabbitmq.queues.observation-created}")
    public void handle(ObservationCreatedEvent event) {
        log.info("Observation created: id={} species={} user={}",
            event.observationId(), event.speciesName(), event.userId());
    }
}
