package com.omyfish.notification.consumer;

import com.omyfish.shared.events.FishIdentifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FishIdentifiedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FishIdentifiedConsumer.class);

    @RabbitListener(queues = "${omyfish.rabbitmq.queues.fish-identified}")
    public void handle(FishIdentifiedEvent event) {
        log.info("Fish identified: species={} confidence={} user={} prediction={}",
            event.topSpeciesName(), event.topConfidence(), event.userId(), event.predictionId());

        // TODO: Push web notification / email when notification channel is added
    }
}
