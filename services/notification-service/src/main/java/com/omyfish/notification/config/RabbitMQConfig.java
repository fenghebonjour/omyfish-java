package com.omyfish.notification.config;

import com.omyfish.shared.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public TopicExchange observationsExchange() {
        return RabbitTopology.observationsExchange();
    }

    @Bean
    public Queue observationCreatedQueue() {
        return RabbitTopology.quorumQueue("omyfish.notifications.observation-created");
    }

    @Bean
    public Binding observationCreatedBinding(Queue observationCreatedQueue, TopicExchange observationsExchange) {
        return RabbitTopology.bind(
            observationCreatedQueue, observationsExchange, RabbitTopology.OBSERVATION_CREATED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange speciesExchange() {
        return RabbitTopology.speciesExchange();
    }

    @Bean
    public Queue fishIdentifiedQueue() {
        return RabbitTopology.quorumQueue("omyfish.notifications.fish-identified");
    }

    @Bean
    public Binding fishIdentifiedBinding(Queue fishIdentifiedQueue, TopicExchange speciesExchange) {
        return RabbitTopology.bind(fishIdentifiedQueue, speciesExchange, RabbitTopology.FISH_IDENTIFIED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return RabbitTopology.jsonMessageConverter();
    }
}
