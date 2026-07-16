package com.omyfish.species.application.service;

import com.omyfish.species.domain.port.in.GetBiteForecastUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;

public class BiteForecastService implements GetBiteForecastUseCase {

    private final AIServicePort aiService;

    public BiteForecastService(AIServicePort aiService) {
        this.aiService = aiService;
    }

    @Override
    public AIServicePort.BiteForecast getForecast(double lat, double lon, String species, int hours) {
        return aiService.getBiteForecast(lat, lon, species, Math.clamp(hours, 1, 336));
    }
}
