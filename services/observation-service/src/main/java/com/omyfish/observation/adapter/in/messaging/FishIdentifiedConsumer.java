package com.omyfish.observation.adapter.in.messaging;

import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.ExifMetadata;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import com.omyfish.shared.events.FishIdentifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FishIdentifiedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FishIdentifiedConsumer.class);

    private final ObservationRepository observationRepository;

    public FishIdentifiedConsumer(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    @RabbitListener(queues = "${omyfish.rabbitmq.queues.fish-identified}")
    public void handle(FishIdentifiedEvent event) {
        log.info("Received FishIdentifiedEvent: predictionId={} species={} confidence={}",
            event.predictionId(), event.topSpeciesName(), event.topConfidence());

        // Observation is typically created by the user explicitly after reviewing predictions.
        // This consumer logs the AI result for analytics / audit trail.
        log.info("AI identified {} with {:.1f}% confidence for user={}",
            event.topSpeciesName(), event.topConfidence() * 100, event.userId());
    }
}
