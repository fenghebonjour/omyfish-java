package com.omyfish.species.adapter.in.web;

import com.omyfish.species.domain.port.in.GetRegsAdvisorUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegsController.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost",
    "omyfish.ai-service.url=http://localhost:8000",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class RegsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean GetRegsAdvisorUseCase getRegsAdvisorUseCase;

    @Test
    void limitsReturnsZoneRules() throws Exception {
        when(getRegsAdvisorUseCase.getLimits(46.8, -71.2, "Brook Trout")).thenReturn(
            new AIServicePort.RegsLimits(46.8, -71.2, "Zone 21", "https://example.org/zone21",
                List.of(new AIServicePort.RegsSpeciesLimit(
                    "Brook Trout", "Apr 24 - Sep 7", "10", "min 20 cm", "line", "quota shared")),
                "Consult the official regs."));

        mvc.perform(get("/api/v1/species/regs/limits")
                .param("lat", "46.8").param("lon", "-71.2").param("species", "Brook Trout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zoneName").value("Zone 21"))
            .andExpect(jsonPath("$.rules[0].catchLimit").value("10"))
            .andExpect(jsonPath("$.rules[0].lengthLimit").value("min 20 cm"));
    }

    @Test
    void limitsDefaultsToGeneralSpecies() throws Exception {
        when(getRegsAdvisorUseCase.getLimits(1.0, 2.0, "general")).thenReturn(
            new AIServicePort.RegsLimits(1.0, 2.0, "Zone 1", null, List.of(), "d"));

        mvc.perform(get("/api/v1/species/regs/limits").param("lat", "1.0").param("lon", "2.0"))
            .andExpect(status().isOk());

        verify(getRegsAdvisorUseCase).getLimits(1.0, 2.0, "general");
    }

    @Test
    void zonesGeoJsonIsPassedThrough() throws Exception {
        when(getRegsAdvisorUseCase.getZonesGeoJson()).thenReturn(Map.of(
            "type", "FeatureCollection",
            "features", List.of(Map.of("type", "Feature", "properties", Map.of("zone", "21")))));

        mvc.perform(get("/api/v1/species/regs/zones/geojson"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FeatureCollection"))
            .andExpect(jsonPath("$.features[0].properties.zone").value("21"));
    }

    @Test
    void consumptionStationsUsesDefaultLimit() throws Exception {
        when(getRegsAdvisorUseCase.getConsumptionStations(46.8, -71.2, 5)).thenReturn(List.of(
            new AIServicePort.RegsStation("BQMA-1", "Riviere Saint-Charles", 46.85, -71.25, 3.2)));

        mvc.perform(get("/api/v1/species/regs/consumption/stations")
                .param("lat", "46.8").param("lon", "-71.2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].noBqma").value("BQMA-1"))
            .andExpect(jsonPath("$[0].distanceKm").value(3.2));
    }

    @Test
    void consumptionForwardsOptionalSize() throws Exception {
        when(getRegsAdvisorUseCase.getConsumption(46.8, -71.2, "Walleye", 42.5)).thenReturn(
            new AIServicePort.RegsConsumption(46.8, -71.2, "Walleye", "BQMA-1", 3.2,
                "40-55 cm", 4, "open", "Limit for children.", "Advisory only."));

        mvc.perform(get("/api/v1/species/regs/consumption")
                .param("lat", "46.8").param("lon", "-71.2")
                .param("species", "Walleye").param("sizeCm", "42.5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mealsPerMonth").value(4))
            .andExpect(jsonPath("$.sizeClass").value("40-55 cm"));
    }

    @Test
    void consumptionWithoutSizeSendsNull() throws Exception {
        when(getRegsAdvisorUseCase.getConsumption(46.8, -71.2, "general", null)).thenReturn(
            new AIServicePort.RegsConsumption(46.8, -71.2, "general", "BQMA-1", 3.2,
                null, null, "open", null, "Advisory only."));

        mvc.perform(get("/api/v1/species/regs/consumption").param("lat", "46.8").param("lon", "-71.2"))
            .andExpect(status().isOk());

        verify(getRegsAdvisorUseCase).getConsumption(46.8, -71.2, "general", null);
    }

    @Test
    void askReturnsAnswerWithSources() throws Exception {
        when(getRegsAdvisorUseCase.ask("Can I fish walleye in May?")).thenReturn(
            new AIServicePort.RegsAnswer("Can I fish walleye in May?", "Zone 21 opens in mid-May.",
                List.of("regs-2026.pdf"), "Advisory only."));

        mvc.perform(post("/api/v1/species/regs/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Can I fish walleye in May?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Zone 21 opens in mid-May."))
            .andExpect(jsonPath("$.sources[0]").value("regs-2026.pdf"));
    }

    @Test
    void aiServiceFailureBecomes503() throws Exception {
        when(getRegsAdvisorUseCase.getZonesGeoJson())
            .thenThrow(new IllegalStateException("ai-service down"));

        mvc.perform(get("/api/v1/species/regs/zones/geojson"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("AI service unavailable"));
    }
}
