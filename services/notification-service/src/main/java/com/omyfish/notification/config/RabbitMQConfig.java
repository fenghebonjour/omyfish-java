package com.omyfish.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
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
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // Use the listener method parameter type for deserialization instead of __TypeId__ header,
        // so the sender's fully-qualified class name doesn't need to be on this classpath.
        converter.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
