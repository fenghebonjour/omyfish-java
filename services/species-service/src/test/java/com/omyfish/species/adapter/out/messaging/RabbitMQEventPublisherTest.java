package com.omyfish.species.adapter.out.messaging;

import com.omyfish.shared.events.FishIdentifiedEvent;
import com.omyfish.species.domain.model.Prediction;
import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.model.valueobject.ConfidenceScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQEventPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;

    private static Prediction prediction(String commonName, double confidence, int rank) {
        Species species = Species.create("Salmo salar", commonName, "Salmonidae", "LC",
            "Rivers", "North Atlantic", "A salmon.", true);
        return Prediction.createRanked(species, "uploads/fish.jpg", ConfidenceScore.of(confidence), rank);
    }

    @Test
    void publishesFishIdentifiedOnTheSpeciesExchange() {
        UUID predictionId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        new RabbitMQEventPublisher(rabbitTemplate).publishFishIdentified(
            predictionId, observationId, userId, "Atlantic Salmon", 0.91,
            List.of(prediction("Atlantic Salmon", 0.91, 1), prediction("Brown Trout", 0.06, 2)),
            "uploads/fish.jpg");

        ArgumentCaptor<FishIdentifiedEvent> event = ArgumentCaptor.forClass(FishIdentifiedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("omyfish.species"), eq("fish.identified"), event.capture());

        FishIdentifiedEvent published = event.getValue();
        assertThat(published.predictionId()).isEqualTo(predictionId);
        assertThat(published.observationId()).isEqualTo(observationId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.topSpeciesName()).isEqualTo("Atlantic Salmon");
        assertThat(published.topConfidence()).isEqualTo(0.91);
        assertThat(published.imageStorageKey()).isEqualTo("uploads/fish.jpg");
        assertThat(published.predictions()).containsExactly(
            new FishIdentifiedEvent.PredictionResult("Atlantic Salmon", 0.91, 1),
            new FishIdentifiedEvent.PredictionResult("Brown Trout", 0.06, 2));
    }

    @Test
    void publishesAnEmptyPredictionListWhenThereAreNoPredictions() {
        UUID id = UUID.randomUUID();

        new RabbitMQEventPublisher(rabbitTemplate).publishFishIdentified(
            id, id, id, "Unknown", 0.0, List.of(), "uploads/fish.jpg");

        ArgumentCaptor<FishIdentifiedEvent> event = ArgumentCaptor.forClass(FishIdentifiedEvent.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), event.capture());

        assertThat(event.getValue().predictions()).isEmpty();
    }
}
