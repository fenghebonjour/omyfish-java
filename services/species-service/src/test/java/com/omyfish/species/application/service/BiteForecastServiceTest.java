package com.omyfish.species.application.service;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiteForecastServiceTest {

    @Mock AIServicePort aiService;

    private static final AIServicePort.BiteForecast FORECAST = new AIServicePort.BiteForecast(
        "salmon", 46.81, -71.21, List.of(), List.of(), List.of(), List.of(), List.of(), null);

    @ParameterizedTest
    @CsvSource({"0, 1", "-5, 1", "1, 1", "24, 24", "336, 336", "337, 336", "10000, 336"})
    void horizonIsClampedToTheSupportedRange(int requestedHours, int expectedHours) {
        when(aiService.getBiteForecast(anyDouble(), anyDouble(), anyString(), anyInt())).thenReturn(FORECAST);

        service().getForecast(46.81, -71.21, "salmon", requestedHours);

        verify(aiService).getBiteForecast(46.81, -71.21, "salmon", expectedHours);
    }

    @Test
    void forecastIsReturnedUnmodified() {
        when(aiService.getBiteForecast(46.81, -71.21, "salmon", 24)).thenReturn(FORECAST);

        assertThat(service().getForecast(46.81, -71.21, "salmon", 24)).isSameAs(FORECAST);
    }

    private BiteForecastService service() {
        return new BiteForecastService(aiService);
    }
}
