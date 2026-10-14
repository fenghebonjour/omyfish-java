package com.omyfish.species.adapter.in.web;

import com.omyfish.species.adapter.in.web.dto.PredictionResponse;
import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import com.omyfish.species.domain.port.out.StoragePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
public class IdentificationController {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_TOP_K = 25;

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
        validateImage(image);
        int boundedTopK = Math.clamp(topK, 1, MAX_TOP_K);
        byte[] imageBytes = image.getBytes();
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        String imageKey = storagePort.store(
            image.getInputStream(), image.getSize(), image.getContentType(), image.getOriginalFilename()
        );

        IdentificationService.IdentificationResult result = identifyFishUseCase.identify(
            new IdentificationService.IdentifyFishCommand(imageBase64, imageKey, boundedTopK, UUID.randomUUID(), UUID.randomUUID())
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

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is required");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 20MB or smaller");
        }
        String contentType = image.getContentType();
        if (contentType == null
            || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase().split(";")[0].trim())) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image type");
        }
    }
}
