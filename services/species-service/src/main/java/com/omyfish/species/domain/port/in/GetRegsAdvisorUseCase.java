package com.omyfish.species.domain.port.in;

import com.omyfish.species.domain.port.out.AIServicePort;
import java.util.Map;

public interface GetRegsAdvisorUseCase {

    AIServicePort.RegsLimits getLimits(double lat, double lon, String species);

    Map<String, Object> getZonesGeoJson();

    java.util.List<AIServicePort.RegsStation> getConsumptionStations(double lat, double lon, int limit);

    AIServicePort.RegsConsumption getConsumption(double lat, double lon, String species, Double sizeCm);

    AIServicePort.RegsAnswer ask(String question);
}
