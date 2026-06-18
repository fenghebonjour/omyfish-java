package com.omyfish.observation.adapter.in.web;

import com.omyfish.observation.adapter.in.web.dto.CreateObservationRequest;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/observations")
public class ObservationController {

    private final CreateObservationUseCase createObservationUseCase;

    public ObservationController(CreateObservationUseCase createObservationUseCase) {
        this.createObservationUseCase = createObservationUseCase;
    }

    @PostMapping
    public ResponseEntity<Observation> create(@RequestBody CreateObservationRequest request) {
        return ResponseEntity.ok(createObservationUseCase.create(request));
    }
}
