package com.omyfish.species.adapter.in.web;

import com.omyfish.species.domain.port.in.GetBiteForecastUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/species/bite-score")
public class BiteScoreController {

    private final GetBiteForecastUseCase getBiteForecastUseCase;

    public BiteScoreController(GetBiteForecastUseCase getBiteForecastUseCase) {
        this.getBiteForecastUseCase = getBiteForecastUseCase;
    }

    @GetMapping("/forecast")
    public ResponseEntity<AIServicePort.BiteForecast> forecast(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "general") String species,
        @RequestParam(defaultValue = "168") int hours
    ) {
        return ResponseEntity.ok(getBiteForecastUseCase.getForecast(lat, lon, species, hours));
    }

    @GetMapping("/today")
    public ResponseEntity<AIServicePort.BiteForecast> today(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "general") String species
    ) {
        return ResponseEntity.ok(getBiteForecastUseCase.getForecast(lat, lon, species, 24));
    }

    @ExceptionHandler({WebClientRequestException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> handleAiServiceDown() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "AI service unavailable",
            "detail", "The bite-score service is unreachable or its weather provider is down. Try again shortly."));
    }
}
