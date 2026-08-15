package com.omyfish.species.adapter.in.web;

import com.omyfish.species.domain.port.in.GetBiteForecastUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BiteScoreController.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost",
    "omyfish.ai-service.url=http://localhost:8000",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class BiteScoreControllerTest {

    @Autowired MockMvc mvc;
    @MockBean GetBiteForecastUseCase getBiteForecastUseCase;

    private static final Map<String, Double> BREAKDOWN = Map.of(
        "pressure", 0.8, "wind", 0.4, "temperature", 0.6,
        "precipitation", 0.9, "cloud_cover", 0.5, "moon", 0.7);

    private static AIServicePort.BiteForecast forecast() {
        AIServicePort.BiteHourlyScore hour = new AIServicePort.BiteHourlyScore(
            LocalDateTime.parse("2026-05-01T06:00:00"), 72.5, BREAKDOWN,
            Map.of("pressure", 0.2), 1.2, null);
        return new AIServicePort.BiteForecast(
            "salmon", 46.81, -71.21, List.of(hour), List.of(hour),
            List.of(new AIServicePort.TimeWindow(
                LocalDateTime.parse("2026-05-01T05:30:00"), LocalDateTime.parse("2026-05-01T07:30:00"))),
            List.of(),
            List.of(new AIServicePort.SunTimes(LocalDate.parse("2026-05-01"),
                LocalDateTime.parse("2026-05-01T05:20:00"), LocalDateTime.parse("2026-05-01T19:45:00"))),
            new AIServicePort.CurrentConditions(LocalDateTime.parse("2026-05-01T09:00:00"), 2.5, true, false));
    }

    @Test
    void forecastPassesThroughTheSixFactorBreakdown() throws Exception {
        when(getBiteForecastUseCase.getForecast(46.81, -71.21, "salmon", 336)).thenReturn(forecast());

        mvc.perform(get("/api/v1/species/bite-score/forecast")
                .param("lat", "46.81").param("lon", "-71.21").param("species", "salmon"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.species").value("salmon"))
            .andExpect(jsonPath("$.hourly[0].score").value(72.5))
            .andExpect(jsonPath("$.hourly[0].breakdown.pressure").value(0.8))
            .andExpect(jsonPath("$.hourly[0].breakdown.moon").value(0.7))
            .andExpect(jsonPath("$.hourly[0].breakdown.cloud_cover").value(0.5))
            .andExpect(jsonPath("$.majorWindows[0].start").value("2026-05-01T05:30:00"))
            .andExpect(jsonPath("$.current.isStorm").value(true));
    }

    @Test
    void forecastDefaultsToGeneralSpeciesAndFullHorizon() throws Exception {
        when(getBiteForecastUseCase.getForecast(1.0, 2.0, "general", 336)).thenReturn(forecast());

        mvc.perform(get("/api/v1/species/bite-score/forecast").param("lat", "1.0").param("lon", "2.0"))
            .andExpect(status().isOk());

        verify(getBiteForecastUseCase).getForecast(1.0, 2.0, "general", 336);
    }

    @Test
    void todayRequestsA24HourHorizon() throws Exception {
        when(getBiteForecastUseCase.getForecast(1.0, 2.0, "general", 24)).thenReturn(forecast());

        mvc.perform(get("/api/v1/species/bite-score/today").param("lat", "1.0").param("lon", "2.0"))
            .andExpect(status().isOk());

        verify(getBiteForecastUseCase).getForecast(1.0, 2.0, "general", 24);
    }

    @Test
    void aiServiceFailureBecomes503() throws Exception {
        when(getBiteForecastUseCase.getForecast(1.0, 2.0, "general", 24))
            .thenThrow(new IllegalStateException("Empty bite-score response from ai-service"));

        mvc.perform(get("/api/v1/species/bite-score/today").param("lat", "1.0").param("lon", "2.0"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("AI service unavailable"));
    }

    @Test
    void missingCoordinatesAreRejected() throws Exception {
        mvc.perform(get("/api/v1/species/bite-score/forecast").param("lat", "1.0"))
            .andExpect(status().isBadRequest());
    }
}
