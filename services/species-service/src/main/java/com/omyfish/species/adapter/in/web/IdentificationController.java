package com.omyfish.species.adapter.in.web;

import com.omyfish.species.adapter.in.web.dto.PredictionResponse;
import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.domain.exception.AiServiceException;
import com.omyfish.species.domain.exception.ImageStorageException;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import com.omyfish.species.domain.port.out.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
public class IdentificationController {

    private static final Logger log = LoggerFactory.getLogger(IdentificationController.class);

    private final IdentifyFishUseCase identifyFishUseCase;
    private final StoragePort storagePort;

    public IdentificationController(IdentifyFishUseCase identifyFishUseCase, StoragePort storagePort) {
        this.identifyFishUseCase = identifyFishUseCase;
        this.storagePort = storagePort;
    }

    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PredictionResponse> identify(
        @RequestParam("image") MultipartFile image,
        @RequestParam(value = "topK", defaultValue = "5") int topK
    ) throws IOException {
        byte[] imageBytes = image.getBytes();
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        String imageKey = storagePort.store(
            image.getInputStream(), image.getSize(), image.getContentType(), image.getOriginalFilename()
        );

        IdentificationService.IdentificationResult result = identifyFishUseCase.identify(
            new IdentificationService.IdentifyFishCommand(imageBase64, imageKey, topK, UUID.randomUUID(), UUID.randomUUID())
        );

        List<PredictionResponse.PredictionItem> items = result.predictions().stream()
            .map(p -> new PredictionResponse.PredictionItem(
                p.getSpecies().getCommonName(),
                p.getSpecies().getScientificName(),
                p.getConfidence().getValue(),
                p.getRank(),
                p.getSpecies().getConservationStatus(),
                p.getSpecies().getHabitat(),
                p.getSpecies().getDiet(),
                p.getSpecies().getMaxSizeCm(),
                p.getSpecies().getDescription(),
                p.getSpecies().getFunFact()
            ))
            .toList();

        return ResponseEntity.ok(new PredictionResponse(items, result.uncertain(), imageKey, result.isFish()));
    }

    @ExceptionHandler({WebClientException.class, AiServiceException.class})
    ResponseEntity<Map<String, String>> handleAiServiceDown(Exception e) {
        log.error("Fish identification failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "AI service unavailable",
            "detail", "The identification service is unreachable. Try again shortly."));
    }

    @ExceptionHandler({ImageStorageException.class, IOException.class})
    ResponseEntity<Map<String, String>> handleStorageFailure(Exception e) {
        log.error("Image upload failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "Image storage unavailable",
            "detail", "The uploaded image could not be stored. Try again shortly."));
    }
}
