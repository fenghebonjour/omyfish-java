package com.omyfish.species.application.service;

import com.omyfish.species.domain.port.in.GetRegsAdvisorUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;

import java.util.List;
import java.util.Map;

public class RegsAdvisorService implements GetRegsAdvisorUseCase {

    private final AIServicePort aiService;

    public RegsAdvisorService(AIServicePort aiService) {
        this.aiService = aiService;
    }

    @Override
    public AIServicePort.RegsLimits getLimits(double lat, double lon, String species) {
        return aiService.getRegsLimits(lat, lon, species);
    }

    @Override
    public Map<String, Object> getZonesGeoJson() {
        return aiService.getRegsZonesGeoJson();
    }

    @Override
    public List<AIServicePort.RegsStation> getConsumptionStations(double lat, double lon, int limit) {
        return aiService.getRegsConsumptionStations(lat, lon, limit);
    }

    @Override
    public AIServicePort.RegsConsumption getConsumption(double lat, double lon, String species, Double sizeCm) {
        return aiService.getRegsConsumption(lat, lon, species, sizeCm);
    }

    @Override
    public AIServicePort.RegsAnswer ask(String question) {
        return aiService.askRegs(question);
    }
}
