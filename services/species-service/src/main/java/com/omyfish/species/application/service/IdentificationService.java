package com.omyfish.species.application.service;

import com.omyfish.species.domain.model.Prediction;
import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.model.valueobject.ConfidenceScore;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import com.omyfish.species.domain.port.out.EventPublisherPort;
import com.omyfish.species.domain.port.out.SpeciesRepository;

import java.util.List;
import java.util.UUID;

public class IdentificationService implements IdentifyFishUseCase {

    private final AIServicePort aiService;
    private final SpeciesRepository speciesRepository;
    private final EventPublisherPort eventPublisher;

    public IdentificationService(
        AIServicePort aiService,
        SpeciesRepository speciesRepository,
        EventPublisherPort eventPublisher
    ) {
        this.aiService = aiService;
        this.speciesRepository = speciesRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public IdentificationResult identify(IdentifyFishCommand command) {
        AIServicePort.AIResult aiResult =
            aiService.predict(command.imageBase64(), command.topK());

        List<Prediction> predictions = aiResult.predictions().stream()
            .map(ai -> {
                Species species = speciesRepository
                    .findByScientificName(ai.scientificName())
                    .orElseGet(() -> Species.create(
                        ai.scientificName(), ai.commonName(),
                        "Unknown", ai.conservationStatus() != null ? ai.conservationStatus() : "Unknown",
                        ai.habitat(), "Unknown", ai.description(),
                        ai.diet(), ai.maxSizeCm(), ai.funFact(), false
                    ));
                return Prediction.createRanked(
                    species,
                    command.imageStorageKey(),
                    ConfidenceScore.of(ai.confidence()),
                    ai.rank()
                );
            })
            .toList();

        if (!predictions.isEmpty()) {
            Prediction top = predictions.get(0);
            eventPublisher.publishFishIdentified(
                UUID.randomUUID(),
                command.observationId(),
                command.userId(),
                top.getSpecies().getCommonName(),
                top.getConfidence().getValue(),
                predictions,
                command.imageStorageKey()
            );
        }

        return new IdentificationResult(predictions, predictions.stream()
            .findFirst()
            .map(p -> p.getConfidence().isUncertain())
            .orElse(true), aiResult.isFish());
    }

    public record IdentifyFishCommand(
        String imageBase64,
        String imageStorageKey,
        int topK,
        UUID observationId,
        UUID userId
    ) {}

    public record IdentificationResult(List<Prediction> predictions, boolean uncertain, boolean isFish) {}
}
