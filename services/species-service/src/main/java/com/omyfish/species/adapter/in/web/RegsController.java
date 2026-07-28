package com.omyfish.species.adapter.in.web;

import com.omyfish.species.domain.port.in.GetRegsAdvisorUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;
import java.util.Map;

/**
 * Thin proxy to omyfish-ai's Regs Advisor feature (frozen, unchanged) — the
 * chatbot/retrieval logic lives only there. Mirrors BiteScoreController's
 * pattern of proxying a species-adjacent AI-service feature.
 */
@RestController
@RequestMapping("/api/v1/species/regs")
public class RegsController {

    private final GetRegsAdvisorUseCase getRegsAdvisorUseCase;

    public RegsController(GetRegsAdvisorUseCase getRegsAdvisorUseCase) {
        this.getRegsAdvisorUseCase = getRegsAdvisorUseCase;
    }

    @GetMapping("/limits")
    public ResponseEntity<AIServicePort.RegsLimits> limits(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "general") String species
    ) {
        return ResponseEntity.ok(getRegsAdvisorUseCase.getLimits(lat, lon, species));
    }

    @GetMapping("/zones/geojson")
    public ResponseEntity<Map<String, Object>> zonesGeoJson() {
        return ResponseEntity.ok(getRegsAdvisorUseCase.getZonesGeoJson());
    }

    @GetMapping("/consumption/stations")
    public ResponseEntity<List<AIServicePort.RegsStation>> consumptionStations(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(getRegsAdvisorUseCase.getConsumptionStations(lat, lon, limit));
    }

    @GetMapping("/consumption")
    public ResponseEntity<AIServicePort.RegsConsumption> consumption(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "general") String species,
        @RequestParam(required = false) Double sizeCm
    ) {
        return ResponseEntity.ok(getRegsAdvisorUseCase.getConsumption(lat, lon, species, sizeCm));
    }

    @PostMapping("/ask")
    public ResponseEntity<AIServicePort.RegsAnswer> ask(@RequestBody AskRequest request) {
        return ResponseEntity.ok(getRegsAdvisorUseCase.ask(request.question()));
    }

    @ExceptionHandler({WebClientRequestException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> handleAiServiceDown() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "AI service unavailable",
            "detail", "The regs advisor is unreachable or its data provider is down. Try again shortly."));
    }

    record AskRequest(String question) {}
}
