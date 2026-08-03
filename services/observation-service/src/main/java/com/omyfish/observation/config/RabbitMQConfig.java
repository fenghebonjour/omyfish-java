package com.omyfish.observation.config;

import com.omyfish.shared.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange speciesExchange() {
        return RabbitTopology.speciesExchange();
    }

    @Bean
    public TopicExchange observationsExchange() {
        return RabbitTopology.observationsExchange();
    }

    @Bean
    public Queue fishIdentifiedQueue() {
        return RabbitTopology.quorumQueue("omyfish.observations.fish-identified");
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
