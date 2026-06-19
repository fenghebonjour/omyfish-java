package com.omyfish.observation.adapter.out.messaging;

import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQEventPublisher implements EventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ObservationCreatedEvent event) {
        rabbitTemplate.convertAndSend("omyfish.observations", "observation.created", event);
    }
}
