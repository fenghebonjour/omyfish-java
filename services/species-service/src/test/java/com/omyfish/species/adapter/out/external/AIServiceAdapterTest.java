package com.omyfish.species.adapter.out.external;

import com.omyfish.species.domain.port.out.AIServicePort;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the adapter against a stub ai-service over real HTTP so the WebClient
 * wiring, query parameters and JSON mapping are all exercised.
 */
class AIServiceAdapterTest {

    private HttpServer server;
    private AIServiceAdapter adapter;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final Map<String, String> receivedQueries = new ConcurrentHashMap<>();
    private final Map<String, String> receivedBodies = new ConcurrentHashMap<>();

    @BeforeEach
    void startStubAiService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        adapter = new AIServiceAdapter("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopStubAiService() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            receivedQueries.put(path, query);
        }
        try (InputStream in = exchange.getRequestBody()) {
            receivedBodies.put(path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] body = responses.getOrDefault(path, "").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void stub(String path, String json) {
        responses.put(path, json);
    }

    // ─── predict ──────────────────────────────────────────────────────────────

    @Test
    void predictMapsPredictionsAndRequestBody() {
        stub("/predict", """
            {"is_fish": true, "predictions": [
              {"scientific_name": "Salmo salar", "common_name": "Atlantic Salmon", "confidence": 0.91,
               "rank": 1, "conservation_status": "LC", "habitat": "Rivers", "diet": "Insects",
               "max_size_cm": 150, "description": "A salmon.", "fun_fact": "Leaps waterfalls."}]}
            """);

        AIServicePort.AIResult result = adapter.predict("YmFzZTY0", 3);

        assertThat(result.isFish()).isTrue();
        assertThat(result.predictions()).singleElement().satisfies(p -> {
            assertThat(p.scientificName()).isEqualTo("Salmo salar");
            assertThat(p.commonName()).isEqualTo("Atlantic Salmon");
            assertThat(p.confidence()).isEqualTo(0.91);
            assertThat(p.rank()).isEqualTo(1);
            assertThat(p.conservationStatus()).isEqualTo("LC");
            assertThat(p.habitat()).isEqualTo("Rivers");
            assertThat(p.diet()).isEqualTo("Insects");
            assertThat(p.maxSizeCm()).isEqualTo(150);
            assertThat(p.description()).isEqualTo("A salmon.");
            assertThat(p.funFact()).isEqualTo("Leaps waterfalls.");
        });
        assertThat(receivedBodies.get("/predict")).contains("\"image_base64\":\"YmFzZTY0\"", "\"top_k\":3");
    }

    @Test
    void predictTreatsMissingIsFishAsFish() {
        stub("/predict", """
            {"predictions": [{"scientific_name": "Esox lucius", "common_name": "Northern Pike",
              "confidence": 0.42, "rank": 1, "conservation_status": null, "habitat": null,
              "diet": null, "max_size_cm": null, "description": null, "fun_fact": null}]}
            """);

        assertThat(adapter.predict("img", 1).isFish()).isTrue();
    }

    @Test
    void predictHonoursNegativeIsFishVerdict() {
        stub("/predict", """
            {"is_fish": false, "predictions": []}
            """);

        AIServicePort.AIResult result = adapter.predict("img", 1);

        assertThat(result.isFish()).isFalse();
        assertThat(result.predictions()).isEmpty();
    }

    @Test
    void predictWithEmptyResponseBodyYieldsNoPredictions() {
        AIServicePort.AIResult result = adapter.predict("img", 1);

        assertThat(result.predictions()).isEmpty();
        assertThat(result.isFish()).isTrue();
    }

    // ─── bite score ───────────────────────────────────────────────────────────

    private static final String FORECAST_JSON = """
        {"species": "salmon", "lat": 46.81, "lon": -71.21,
         "hourly": [{"timestamp": "2026-05-01T06:00:00", "score": 72.5,
             "breakdown": {"pressure": 0.8, "wind": 0.4, "temperature": 0.6,
                           "precipitation": 0.9, "cloud_cover": 0.5, "moon": 0.7},
             "weighted_contribution": {"pressure": 0.2}, "time_of_day_multiplier": 1.2,
             "safety_flag": null}],
         "best_windows": [{"timestamp": "2026-05-01T19:00:00", "score": 88.0,
             "breakdown": {"pressure": 0.9}, "weighted_contribution": {"pressure": 0.3},
             "time_of_day_multiplier": 1.3, "safety_flag": "storm"}],
         "major_windows": [{"start": "2026-05-01T05:30:00", "end": "2026-05-01T07:30:00"}],
         "minor_windows": [{"start": "2026-05-01T12:00:00", "end": "2026-05-01T13:00:00"}],
         "sun_times": [{"date": "2026-05-01", "sunrise": "2026-05-01T05:20:00",
                        "sunset": "2026-05-01T19:45:00"}],
         "current": {"time": "2026-05-01T09:00:00", "precipitation_mm": 2.5,
                     "is_storm": true, "is_heavy_precip": false}}
        """;

    @Test
    void biteForecastResolvesSpeciesKeyAndMapsSixFactorBreakdown() {
        stub("/bite-score/species-key",
            "{\"input\": \"Atlantic Salmon\", \"species_key\": \"salmon\", \"matched\": true}");
        stub("/bite-score/forecast", FORECAST_JSON);

        AIServicePort.BiteForecast forecast = adapter.getBiteForecast(46.81, -71.21, "Atlantic Salmon", 24);

        assertThat(receivedQueries.get("/bite-score/species-key")).contains("name=Atlantic Salmon");
        assertThat(receivedQueries.get("/bite-score/forecast"))
            .contains("lat=46.81", "lon=-71.21", "species=salmon", "hours=24");
        assertThat(forecast.species()).isEqualTo("salmon");
        assertThat(forecast.lat()).isEqualTo(46.81);
        assertThat(forecast.lon()).isEqualTo(-71.21);
        assertThat(forecast.hourly()).singleElement().satisfies(h -> {
            assertThat(h.timestamp()).isEqualTo(LocalDateTime.parse("2026-05-01T06:00:00"));
            assertThat(h.score()).isEqualTo(72.5);
            assertThat(h.breakdown()).containsOnlyKeys(
                "pressure", "wind", "temperature", "precipitation", "cloud_cover", "moon");
            assertThat(h.weightedContribution()).containsEntry("pressure", 0.2);
            assertThat(h.timeOfDayMultiplier()).isEqualTo(1.2);
            assertThat(h.safetyFlag()).isNull();
        });
        assertThat(forecast.bestWindows()).singleElement()
            .satisfies(w -> assertThat(w.safetyFlag()).isEqualTo("storm"));
        assertThat(forecast.majorWindows()).containsExactly(new AIServicePort.TimeWindow(
            LocalDateTime.parse("2026-05-01T05:30:00"), LocalDateTime.parse("2026-05-01T07:30:00")));
        assertThat(forecast.minorWindows()).containsExactly(new AIServicePort.TimeWindow(
            LocalDateTime.parse("2026-05-01T12:00:00"), LocalDateTime.parse("2026-05-01T13:00:00")));
        assertThat(forecast.sunTimes()).containsExactly(new AIServicePort.SunTimes(
            LocalDate.parse("2026-05-01"), LocalDateTime.parse("2026-05-01T05:20:00"),
            LocalDateTime.parse("2026-05-01T19:45:00")));
        assertThat(forecast.current()).isEqualTo(new AIServicePort.CurrentConditions(
            LocalDateTime.parse("2026-05-01T09:00:00"), 2.5, true, false));
    }

    @Test
    void biteForecastFallsBackToGeneralWhenSpeciesKeyLookupIsEmpty() {
        stub("/bite-score/forecast", FORECAST_JSON);

        adapter.getBiteForecast(46.81, -71.21, "Unknown Fish", 48);

        assertThat(receivedQueries.get("/bite-score/forecast")).contains("species=general");
    }

    @Test
    void biteForecastToleratesAnAiServiceWithoutSolunarOrNowcastFields() {
        stub("/bite-score/species-key", "{\"input\": \"general\", \"species_key\": \"general\", \"matched\": false}");
        stub("/bite-score/forecast", """
            {"species": "general", "lat": 1.0, "lon": 2.0, "hourly": [], "best_windows": [],
             "major_windows": null, "minor_windows": null, "sun_times": null, "current": null}
            """);

        AIServicePort.BiteForecast forecast = adapter.getBiteForecast(1.0, 2.0, "general", 24);

        assertThat(forecast.majorWindows()).isEmpty();
        assertThat(forecast.minorWindows()).isEmpty();
        assertThat(forecast.sunTimes()).isEmpty();
        assertThat(forecast.current()).isNull();
    }

    @Test
    void biteForecastDefaultsMissingNowcastNumbers() {
        stub("/bite-score/forecast", """
            {"species": "general", "lat": 1.0, "lon": 2.0, "hourly": [], "best_windows": [],
             "major_windows": [], "minor_windows": [], "sun_times": [],
             "current": {"time": "2026-05-01T09:00:00", "precipitation_mm": null,
                         "is_storm": null, "is_heavy_precip": null}}
            """);

        AIServicePort.CurrentConditions current = adapter.getBiteForecast(1.0, 2.0, "general", 24).current();

        assertThat(current.precipitationMm()).isZero();
        assertThat(current.isStorm()).isFalse();
        assertThat(current.isHeavyPrecip()).isFalse();
    }

    @Test
    void biteForecastFailsLoudlyOnEmptyResponse() {
        assertThatThrownBy(() -> adapter.getBiteForecast(1.0, 2.0, "general", 24))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Empty bite-score response");
    }

    // ─── regs advisor ─────────────────────────────────────────────────────────

    @Test
    void regsLimitsMapsZoneRules() {
        stub("/regs/limits", """
            {"lat": 46.8, "lon": -71.2, "zone_name": "Zone 21", "zone_info_url": "https://mffp.gouv.qc.ca/zone21",
             "rules": [{"species": "Brook Trout", "period": "Apr 24 - Sep 7", "catch_limit": "10",
                        "length_limit": "min 20 cm", "fishing_device": "line", "note": "quota shared"}],
             "disclaimer": "Consult the official regs."}
            """);

        AIServicePort.RegsLimits limits = adapter.getRegsLimits(46.8, -71.2, "Brook Trout");

        assertThat(receivedQueries.get("/regs/limits"))
            .contains("lat=46.8", "lon=-71.2", "species=Brook Trout");
        assertThat(limits.zoneName()).isEqualTo("Zone 21");
        assertThat(limits.zoneInfoUrl()).isEqualTo("https://mffp.gouv.qc.ca/zone21");
        assertThat(limits.disclaimer()).isEqualTo("Consult the official regs.");
        assertThat(limits.rules()).containsExactly(new AIServicePort.RegsSpeciesLimit(
            "Brook Trout", "Apr 24 - Sep 7", "10", "min 20 cm", "line", "quota shared"));
    }

    @Test
    void regsLimitsFailsLoudlyOnEmptyResponse() {
        assertThatThrownBy(() -> adapter.getRegsLimits(1.0, 2.0, "general"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Empty regs limits response");
    }

    @Test
    void regsZonesGeoJsonIsPassedThroughUntouched() {
        stub("/regs/zones/geojson", """
            {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"zone": "21"}}]}
            """);

        Map<String, Object> geoJson = adapter.getRegsZonesGeoJson();

        assertThat(geoJson).containsEntry("type", "FeatureCollection");
        assertThat(geoJson.get("features")).isInstanceOf(List.class);
    }

    @Test
    void regsZonesGeoJsonReturnsEmptyMapWhenAiServiceSendsNothing() {
        assertThat(adapter.getRegsZonesGeoJson()).isEmpty();
    }

    @Test
    void consumptionStationsMapsNearestStations() {
        stub("/regs/consumption/stations", """
            [{"no_bqma": "BQMA-1", "hydronyme": "Riviere Saint-Charles",
              "latitude": 46.85, "longitude": -71.25, "distance_km": 3.2}]
            """);

        List<AIServicePort.RegsStation> stations = adapter.getRegsConsumptionStations(46.8, -71.2, 5);

        assertThat(receivedQueries.get("/regs/consumption/stations"))
            .contains("lat=46.8", "lon=-71.2", "limit=5");
        assertThat(stations).containsExactly(new AIServicePort.RegsStation(
            "BQMA-1", "Riviere Saint-Charles", 46.85, -71.25, 3.2));
    }

    @Test
    void consumptionStationsReturnsEmptyListWhenAiServiceSendsNothing() {
        assertThat(adapter.getRegsConsumptionStations(1.0, 2.0, 5)).isEmpty();
    }

    @Test
    void consumptionAdvisoryIncludesSizeWhenProvided() {
        stub("/regs/consumption", """
            {"lat": 46.8, "lon": -71.2, "species": "Walleye", "station_name": "BQMA-1", "distance_km": 3.2,
             "size_class": "40-55 cm", "meals_per_month": 4, "fishing_status": "open",
             "note": "Limit for children.", "disclaimer": "Advisory only."}
            """);

        AIServicePort.RegsConsumption consumption = adapter.getRegsConsumption(46.8, -71.2, "Walleye", 42.5);

        assertThat(receivedQueries.get("/regs/consumption")).contains("size_cm=42.5");
        assertThat(consumption.sizeClass()).isEqualTo("40-55 cm");
        assertThat(consumption.mealsPerMonth()).isEqualTo(4);
        assertThat(consumption.stationName()).isEqualTo("BQMA-1");
        assertThat(consumption.fishingStatus()).isEqualTo("open");
    }

    @Test
    void consumptionAdvisoryOmitsSizeParamWhenNull() {
        stub("/regs/consumption", """
            {"lat": 46.8, "lon": -71.2, "species": "Walleye", "station_name": "BQMA-1", "distance_km": 3.2,
             "size_class": null, "meals_per_month": null, "fishing_status": "open",
             "note": null, "disclaimer": "Advisory only."}
            """);

        adapter.getRegsConsumption(46.8, -71.2, "Walleye", null);

        assertThat(receivedQueries.get("/regs/consumption")).doesNotContain("size_cm");
    }

    @Test
    void consumptionAdvisoryFailsLoudlyOnEmptyResponse() {
        assertThatThrownBy(() -> adapter.getRegsConsumption(1.0, 2.0, "general", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Empty regs consumption response");
    }

    @Test
    void askRegsForwardsQuestionAndMapsAnswer() {
        stub("/regs/ask", """
            {"question": "Can I fish walleye in May?", "answer": "Zone 21 opens in mid-May.",
             "sources": ["regs-2026.pdf"], "disclaimer": "Advisory only."}
            """);

        AIServicePort.RegsAnswer answer = adapter.askRegs("Can I fish walleye in May?");

        assertThat(receivedBodies.get("/regs/ask")).contains("Can I fish walleye in May?");
        assertThat(answer.answer()).isEqualTo("Zone 21 opens in mid-May.");
        assertThat(answer.sources()).containsExactly("regs-2026.pdf");
        assertThat(answer.disclaimer()).isEqualTo("Advisory only.");
    }

    @Test
    void askRegsFailsLoudlyOnEmptyResponse() {
        assertThatThrownBy(() -> adapter.askRegs("anything"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Empty regs ask response");
    }
}
