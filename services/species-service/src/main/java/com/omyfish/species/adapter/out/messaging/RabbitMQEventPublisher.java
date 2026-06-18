package com.omyfish.species.adapter.out.messaging;

import com.omyfish.shared.events.FishIdentifiedEvent;
import com.omyfish.species.domain.model.Prediction;
import com.omyfish.species.domain.port.out.EventPublisherPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RabbitMQEventPublisher implements EventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishFishIdentified(
        UUID predictionId,
        UUID observationId,
        UUID userId,
        String topSpeciesName,
        double topConfidence,
        List<Prediction> predictions,
        String imageStorageKey
    ) {
        List<FishIdentifiedEvent.PredictionResult> results = predictions.stream()
            .map(p -> new FishIdentifiedEvent.PredictionResult(
                p.getSpecies().getCommonName(), p.getConfidence().getValue(), p.getRank()
            ))
            .toList();

        FishIdentifiedEvent event = new FishIdentifiedEvent(
            predictionId, observationId, userId,
            topSpeciesName, topConfidence, results, imageStorageKey
        );

        rabbitTemplate.convertAndSend(FishIdentifiedEvent.EXCHANGE, FishIdentifiedEvent.ROUTING_KEY, event);
    }
}
