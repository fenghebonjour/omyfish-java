package com.omyfish.species.domain.port.in;

import com.omyfish.species.domain.port.out.AIServicePort;

public interface GetBiteForecastUseCase {

    AIServicePort.BiteForecast getForecast(double lat, double lon, String species, int hours);
}
