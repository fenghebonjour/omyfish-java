package com.omyfish.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange observationsExchange() {
        return new TopicExchange("omyfish.observations");
    }

    @Bean
    public Queue observationCreatedQueue() {
        return QueueBuilder.durable("omyfish.notifications.observation-created")
            .withArgument("x-queue-type", "quorum")
            .build();
    }

    @Bean
    public Binding observationCreatedBinding(Queue observationCreatedQueue, TopicExchange observationsExchange) {
        return BindingBuilder.bind(observationCreatedQueue).to(observationsExchange).with("observation.created");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
