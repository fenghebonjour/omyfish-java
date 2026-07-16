package com.omyfish.species.adapter.out.external;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class AIServiceAdapter implements AIServicePort {

    private final WebClient webClient;

    public AIServiceAdapter(@Value("${omyfish.ai-service.url}") String aiServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(aiServiceUrl).build();
    }

    @Override
    public AIResult predict(String imageBase64, int topK) {
        AIResponse response = webClient.post()
            .uri("/predict")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new PredictRequest(imageBase64, topK))
            .retrieve()
            .bodyToMono(AIResponse.class)
            .block();

        if (response == null) return new AIResult(List.of(), true);

        List<AIPrediction> predictions = response.predictions().stream()
            .map(p -> new AIPrediction(
                p.scientific_name(), p.common_name(), p.confidence(), p.rank(), p.conservation_status(),
                p.habitat(), p.diet(), p.max_size_cm(), p.description(), p.fun_fact()))
            .toList();
        return new AIResult(predictions, response.is_fish() == null || response.is_fish());
    }

    @Override
    public BiteForecast getBiteForecast(double lat, double lon, String species, int hours) {
        // Resolve first so callers can pass a confirmed fish-ID name directly;
        // unknown species fall back to the "general" profile instead of a 400.
        SpeciesKeyResponse keyResponse = webClient.get()
            .uri(b -> b.path("/bite-score/species-key").queryParam("name", species).build())
            .retrieve()
            .bodyToMono(SpeciesKeyResponse.class)
            .block();
        String speciesKey = keyResponse != null ? keyResponse.species_key() : "general";

        BiteForecastDto dto = webClient.get()
            .uri((UriBuilder b) -> b.path("/bite-score/forecast")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("species", speciesKey)
                .queryParam("hours", hours)
                .build())
            .retrieve()
            .bodyToMono(BiteForecastDto.class)
            .block();

        if (dto == null) {
            throw new IllegalStateException("Empty bite-score response from ai-service");
        }
        return new BiteForecast(
            dto.species(), dto.lat(), dto.lon(),
            dto.hourly().stream().map(AIServiceAdapter::toScore).toList(),
            dto.best_windows().stream().map(AIServiceAdapter::toScore).toList(),
            toWindows(dto.major_windows()),
            toWindows(dto.minor_windows()));
    }

    private static BiteHourlyScore toScore(BiteHourlyScoreDto h) {
        return new BiteHourlyScore(
            h.timestamp(), h.score(), h.breakdown(), h.weighted_contribution(),
            h.time_of_day_multiplier(), h.safety_flag());
    }

    // Null-safe: an ai-service image predating solunar windows omits the fields.
    private static List<TimeWindow> toWindows(List<TimeWindowDto> windows) {
        if (windows == null) return List.of();
        return windows.stream().map(w -> new TimeWindow(w.start(), w.end())).toList();
    }

    private record PredictRequest(String image_base64, int top_k) {}
    private record SpeciesKeyResponse(String input, String species_key, boolean matched) {}
    private record BiteForecastDto(
        String species, double lat, double lon,
        List<BiteHourlyScoreDto> hourly, List<BiteHourlyScoreDto> best_windows,
        List<TimeWindowDto> major_windows, List<TimeWindowDto> minor_windows) {}
    private record TimeWindowDto(LocalDateTime start, LocalDateTime end) {}
    private record BiteHourlyScoreDto(
        LocalDateTime timestamp, double score,
        Map<String, Double> breakdown, Map<String, Double> weighted_contribution,
        double time_of_day_multiplier, String safety_flag) {}
    private record AIResponse(List<AIPredictionDto> predictions, Boolean is_fish) {}
    private record AIPredictionDto(
        String scientific_name, String common_name, double confidence, int rank, String conservation_status,
        String habitat, String diet, Integer max_size_cm, String description, String fun_fact) {}
}
