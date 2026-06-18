package com.omyfish.species.adapter.out.external;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.beans.factory.annotation.Value;
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
    public List<AIPrediction> predict(String imageStorageKey, int topK) {
        AIResponse response = webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/predict")
                .queryParam("image_key", imageStorageKey)
                .queryParam("top_k", topK)
                .build())
            .retrieve()
            .bodyToMono(AIResponse.class)
            .block();

        if (response == null) return List.of();

        return response.predictions().stream()
            .map(p -> new AIPrediction(p.scientific_name(), p.common_name(), p.confidence(), p.rank()))
            .toList();
    }

    private record AIResponse(List<AIPredictionDto> predictions) {}
    private record AIPredictionDto(String scientific_name, String common_name, double confidence, int rank) {}
}
