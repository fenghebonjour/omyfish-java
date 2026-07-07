package com.omyfish.species.adapter.out.external;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

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

    private record PredictRequest(String image_base64, int top_k) {}
    private record AIResponse(List<AIPredictionDto> predictions, Boolean is_fish) {}
    private record AIPredictionDto(
        String scientific_name, String common_name, double confidence, int rank, String conservation_status,
        String habitat, String diet, Integer max_size_cm, String description, String fun_fact) {}
}
