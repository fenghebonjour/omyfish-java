package com.omyfish.shop.config;

import com.omyfish.shared.events.OrderPlacedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // 3 partitions: enough for the demo's consumer-group scaling story without over-provisioning
    // a single-broker dev cluster; events for the same productId still land on the same
    // partition (OrderPlacedEvent.partitionKey()), so per-SKU ordering holds regardless of count.
    @Bean
    NewTopic shopOrdersTopic() {
        return TopicBuilder.name(OrderPlacedEvent.TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
