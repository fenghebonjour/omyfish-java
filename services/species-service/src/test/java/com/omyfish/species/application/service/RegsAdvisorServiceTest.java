package com.omyfish.species.application.service;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegsAdvisorServiceTest {

    @Mock AIServicePort aiService;

    @Test
    void limitsAreDelegatedToTheAiService() {
        AIServicePort.RegsLimits limits = new AIServicePort.RegsLimits(
            46.8, -71.2, "Zone 21", null, List.of(), "Advisory only.");
        when(aiService.getRegsLimits(46.8, -71.2, "Brook Trout")).thenReturn(limits);

        assertThat(service().getLimits(46.8, -71.2, "Brook Trout")).isSameAs(limits);
    }

    @Test
    void zonesGeoJsonIsDelegatedUntouched() {
        Map<String, Object> geoJson = Map.of("type", "FeatureCollection", "features", List.of());
        when(aiService.getRegsZonesGeoJson()).thenReturn(geoJson);

        assertThat(service().getZonesGeoJson()).isSameAs(geoJson);
    }

    @Test
    void consumptionStationsAreDelegated() {
        List<AIServicePort.RegsStation> stations = List.of(
            new AIServicePort.RegsStation("BQMA-1", "Riviere Saint-Charles", 46.85, -71.25, 3.2));
        when(aiService.getRegsConsumptionStations(46.8, -71.2, 3)).thenReturn(stations);

        assertThat(service().getConsumptionStations(46.8, -71.2, 3)).isSameAs(stations);
    }

    @Test
    void consumptionAdvisoryIsDelegatedWithOptionalSize() {
        AIServicePort.RegsConsumption consumption = new AIServicePort.RegsConsumption(
            46.8, -71.2, "Walleye", "BQMA-1", 3.2, "40-55 cm", 4, "open", null, "Advisory only.");
        when(aiService.getRegsConsumption(46.8, -71.2, "Walleye", null)).thenReturn(consumption);

        assertThat(service().getConsumption(46.8, -71.2, "Walleye", null)).isSameAs(consumption);
    }

    @Test
    void questionsAreDelegated() {
        AIServicePort.RegsAnswer answer = new AIServicePort.RegsAnswer(
            "q", "a", List.of("regs-2026.pdf"), "Advisory only.");
        when(aiService.askRegs("q")).thenReturn(answer);

        assertThat(service().ask("q")).isSameAs(answer);
    }

    private RegsAdvisorService service() {
        return new RegsAdvisorService(aiService);
    }
}
