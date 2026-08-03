package com.omyfish.shared.messaging;

import com.omyfish.shared.events.FishIdentifiedEvent;
import com.omyfish.shared.events.ObservationCreatedEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Shared RabbitMQ topology: exchange names, routing keys and the builders every
 * service needs (quorum queues, topic bindings, JSON converter).
 */
public final class RabbitTopology {

    public static final String SPECIES_EXCHANGE = FishIdentifiedEvent.EXCHANGE;
    public static final String OBSERVATIONS_EXCHANGE = ObservationCreatedEvent.EXCHANGE;

    public static final String FISH_IDENTIFIED_ROUTING_KEY = FishIdentifiedEvent.ROUTING_KEY;
    public static final String OBSERVATION_CREATED_ROUTING_KEY = ObservationCreatedEvent.ROUTING_KEY;

    private static final String QUEUE_TYPE_ARGUMENT = "x-queue-type";
    private static final String QUORUM = "quorum";

    private RabbitTopology() {
    }

    public static TopicExchange speciesExchange() {
        return new TopicExchange(SPECIES_EXCHANGE);
    }

    public static TopicExchange observationsExchange() {
        return new TopicExchange(OBSERVATIONS_EXCHANGE);
    }

    public static Queue quorumQueue(String name) {
        return QueueBuilder.durable(name).withArgument(QUEUE_TYPE_ARGUMENT, QUORUM).build();
    }

    public static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    /**
     * Uses the listener method parameter type for deserialization instead of the
     * {@code __TypeId__} header, so the sender's fully-qualified class name does
     * not need to be on the consumer's classpath.
     */
    public static MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
