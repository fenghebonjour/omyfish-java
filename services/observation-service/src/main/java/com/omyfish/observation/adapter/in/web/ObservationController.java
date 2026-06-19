package com.omyfish.observation.adapter.in.web;

import com.omyfish.observation.adapter.in.web.dto.CreateObservationRequest;
import com.omyfish.observation.adapter.in.web.dto.ObservationResponse;
import com.omyfish.observation.domain.exception.ObservationNotFoundException;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.GetObservationUseCase;
import com.omyfish.observation.domain.port.in.ListObservationsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/observations")
public class ObservationController {

    private final CreateObservationUseCase createObservationUseCase;
    private final GetObservationUseCase getObservationUseCase;
    private final ListObservationsUseCase listObservationsUseCase;

    public ObservationController(
        CreateObservationUseCase createObservationUseCase,
        GetObservationUseCase getObservationUseCase,
        ListObservationsUseCase listObservationsUseCase
    ) {
        this.createObservationUseCase = createObservationUseCase;
        this.getObservationUseCase = getObservationUseCase;
        this.listObservationsUseCase = listObservationsUseCase;
    }

    @PostMapping
    public ResponseEntity<ObservationResponse> create(
        @RequestBody CreateObservationRequest request,
        @RequestHeader("X-User-Id") String userIdHeader
    ) {
        CreateObservationUseCase.CreateCommand command = new CreateObservationUseCase.CreateCommand(
            UUID.fromString(userIdHeader),
            request.speciesName(),
            request.scientificName(),
            request.topConfidence(),
            request.imageStorageKey(),
            request.latitude(),
            request.longitude(),
            request.notes()
        );
        return ResponseEntity.ok(ObservationResponse.from(createObservationUseCase.create(command)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObservationResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ObservationResponse.from(getObservationUseCase.get(id)));
    }

    @GetMapping
    public ResponseEntity<List<ObservationResponse>> list(
        @RequestHeader("X-User-Id") String userIdHeader
    ) {
        List<ObservationResponse> responses = listObservationsUseCase
            .listByUser(UUID.fromString(userIdHeader))
            .stream().map(ObservationResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    @ExceptionHandler(ObservationNotFoundException.class)
    ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }
}
