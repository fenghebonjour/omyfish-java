package com.omyfish.species.adapter.in.web;

import com.omyfish.species.adapter.in.web.dto.IdentifyFishRequest;
import com.omyfish.species.adapter.in.web.dto.PredictionResponse;
import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
public class IdentificationController {

    private final IdentifyFishUseCase identifyFishUseCase;

    public IdentificationController(IdentifyFishUseCase identifyFishUseCase) {
        this.identifyFishUseCase = identifyFishUseCase;
    }

    @PostMapping("/identify")
    public ResponseEntity<PredictionResponse> identify(@RequestBody IdentifyFishRequest request) {
        IdentificationService.IdentificationResult result = identifyFishUseCase.identify(
            new IdentificationService.IdentifyFishCommand(
                request.imageStorageKey(),
                request.topK() > 0 ? request.topK() : 5,
                request.observationId() != null ? request.observationId() : UUID.randomUUID(),
                request.userId() != null ? request.userId() : UUID.randomUUID()
            )
        );

        List<PredictionResponse.PredictionItem> items = result.predictions().stream()
            .map(p -> new PredictionResponse.PredictionItem(
                p.getSpecies().getCommonName(),
                p.getSpecies().getScientificName(),
                p.getConfidence().getValue(),
                p.getRank(),
                p.getSpecies().getConservationStatus()
            ))
            .toList();

        return ResponseEntity.ok(new PredictionResponse(items, result.uncertain(), request.imageStorageKey()));
    }
}
