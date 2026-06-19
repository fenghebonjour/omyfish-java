package com.omyfish.observation.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange speciesExchange() {
        return new TopicExchange("omyfish.species");
    }

    @Bean
    public TopicExchange observationsExchange() {
        return new TopicExchange("omyfish.observations");
    }

    @Bean
    public Queue fishIdentifiedQueue() {
        return QueueBuilder.durable("omyfish.observations.fish-identified")
            .withArgument("x-queue-type", "quorum")
            .build();
    }

    @Bean
    public Binding fishIdentifiedBinding(Queue fishIdentifiedQueue, TopicExchange speciesExchange) {
        return BindingBuilder.bind(fishIdentifiedQueue).to(speciesExchange).with("fish.identified");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
