package com.omyfish.species.adapter.out.external;

import com.omyfish.species.domain.port.out.AIServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class AIServiceAdapter implements AIServicePort {

    private final WebClient webClient;

    public AIServiceAdapter(@Value("${omyfish.ai-service.url}") String aiServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(aiServiceUrl).build();
    }

    @Override
    public AIResult predict(String imageBase64, int topK) {
        AIResponse response = post("/predict", new PredictRequest(imageBase64, topK), AIResponse.class);

        if (response == null) return new AIResult(List.of(), true);

        List<AIPrediction> predictions = response.predictions().stream()
            .map(p -> new AIPrediction(
                p.scientific_name(), p.common_name(), p.confidence(), p.rank(), p.conservation_status(),
                p.habitat(), p.diet(), p.max_size_cm(), p.description(), p.fun_fact()))
            .toList();
        return new AIResult(predictions, response.is_fish() == null || response.is_fish());
    }

    @Override
    public BiteForecast getBiteForecast(double lat, double lon, String species, int hours) {
        // Resolve first so callers can pass a confirmed fish-ID name directly;
        // unknown species fall back to the "general" profile instead of a 400.
        SpeciesKeyResponse keyResponse = get(
            b -> b.path("/bite-score/species-key").queryParam("name", species).build(), SpeciesKeyResponse.class);
        String speciesKey = keyResponse != null ? keyResponse.species_key() : "general";

        BiteForecastDto dto = required(get(
            b -> b.path("/bite-score/forecast")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("species", speciesKey)
                .queryParam("hours", hours)
                .build(),
            BiteForecastDto.class), "bite-score");
        return new BiteForecast(
            dto.species(), dto.lat(), dto.lon(),
            dto.hourly().stream().map(AIServiceAdapter::toScore).toList(),
            dto.best_windows().stream().map(AIServiceAdapter::toScore).toList(),
            toWindows(dto.major_windows()),
            toWindows(dto.minor_windows()),
            toSunTimes(dto.sun_times()),
            toCurrent(dto.current()));
    }

    @Override
    public RegsLimits getRegsLimits(double lat, double lon, String species) {
        RegsLimitsDto dto = required(get(
            b -> b.path("/regs/limits")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("species", species)
                .build(),
            RegsLimitsDto.class), "regs limits");
        List<RegsSpeciesLimit> rules = dto.rules().stream()
            .map(r -> new RegsSpeciesLimit(
                r.species(), r.period(), r.catch_limit(), r.length_limit(), r.fishing_device(), r.note()))
            .toList();
        return new RegsLimits(dto.lat(), dto.lon(), dto.zone_name(), dto.zone_info_url(), rules, dto.disclaimer());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRegsZonesGeoJson() {
        Map<String, Object> geoJson = get(b -> b.path("/regs/zones/geojson").build(), Map.class);
        return geoJson != null ? geoJson : Map.of();
    }

    @Override
    public List<RegsStation> getRegsConsumptionStations(double lat, double lon, int limit) {
        RegsStationDto[] stations = get(
            b -> b.path("/regs/consumption/stations")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("limit", limit)
                .build(),
            RegsStationDto[].class);
        if (stations == null) return List.of();
        return List.of(stations).stream()
            .map(s -> new RegsStation(s.no_bqma(), s.hydronyme(), s.latitude(), s.longitude(), s.distance_km()))
            .toList();
    }

    @Override
    public RegsConsumption getRegsConsumption(double lat, double lon, String species, Double sizeCm) {
        RegsConsumptionDto dto = required(get(
            b -> {
                var uri = b.path("/regs/consumption")
                    .queryParam("lat", lat)
                    .queryParam("lon", lon)
                    .queryParam("species", species);
                if (sizeCm != null) uri.queryParam("size_cm", sizeCm);
                return uri.build();
            },
            RegsConsumptionDto.class), "regs consumption");
        return new RegsConsumption(
            dto.lat(), dto.lon(), dto.species(), dto.station_name(), dto.distance_km(),
            dto.size_class(), dto.meals_per_month(), dto.fishing_status(), dto.note(), dto.disclaimer());
    }

    @Override
    public RegsAnswer askRegs(String question) {
        RegsAskResponseDto dto = required(
            post("/regs/ask", new RegsAskRequest(question), RegsAskResponseDto.class), "regs ask");
        return new RegsAnswer(dto.question(), dto.answer(), dto.sources(), dto.disclaimer());
    }

    private <T> T get(Function<UriBuilder, URI> uri, Class<T> responseType) {
        return webClient.get()
            .uri(uri)
            .retrieve()
            .bodyToMono(responseType)
            .block();
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return webClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(responseType)
            .block();
    }

    private static <T> T required(T response, String what) {
        if (response == null) {
            throw new IllegalStateException("Empty " + what + " response from ai-service");
        }
        return response;
    }

    private static BiteHourlyScore toScore(BiteHourlyScoreDto h) {
        return new BiteHourlyScore(
            h.timestamp(), h.score(), h.breakdown(), h.weighted_contribution(),
            h.time_of_day_multiplier(), h.safety_flag());
    }

    // Null-safe: an ai-service image predating solunar windows omits the fields.
    private static List<TimeWindow> toWindows(List<TimeWindowDto> windows) {
        if (windows == null) return List.of();
        return windows.stream().map(w -> new TimeWindow(w.start(), w.end())).toList();
    }

    private static List<SunTimes> toSunTimes(List<SunTimesDto> sunTimes) {
        if (sunTimes == null) return List.of();
        return sunTimes.stream().map(s -> new SunTimes(s.date(), s.sunrise(), s.sunset())).toList();
    }

    private static CurrentConditions toCurrent(CurrentDto c) {
        if (c == null) return null;
        return new CurrentConditions(
            c.time(),
            c.precipitation_mm() == null ? 0.0 : c.precipitation_mm(),
            Boolean.TRUE.equals(c.is_storm()),
            Boolean.TRUE.equals(c.is_heavy_precip()));
    }

    private record PredictRequest(String image_base64, int top_k) {}
    private record SpeciesKeyResponse(String input, String species_key, boolean matched) {}
    private record BiteForecastDto(
        String species, double lat, double lon,
        List<BiteHourlyScoreDto> hourly, List<BiteHourlyScoreDto> best_windows,
        List<TimeWindowDto> major_windows, List<TimeWindowDto> minor_windows,
        List<SunTimesDto> sun_times, CurrentDto current) {}
    private record TimeWindowDto(LocalDateTime start, LocalDateTime end) {}
    private record SunTimesDto(LocalDate date, LocalDateTime sunrise, LocalDateTime sunset) {}
    private record CurrentDto(
        LocalDateTime time, Double precipitation_mm, Boolean is_storm, Boolean is_heavy_precip) {}
    private record BiteHourlyScoreDto(
        LocalDateTime timestamp, double score,
        Map<String, Double> breakdown, Map<String, Double> weighted_contribution,
        double time_of_day_multiplier, String safety_flag) {}
    private record AIResponse(List<AIPredictionDto> predictions, Boolean is_fish) {}
    private record AIPredictionDto(
        String scientific_name, String common_name, double confidence, int rank, String conservation_status,
        String habitat, String diet, Integer max_size_cm, String description, String fun_fact) {}

    private record RegsSpeciesLimitDto(
        String species, String period, String catch_limit,
        String length_limit, String fishing_device, String note) {}
    private record RegsLimitsDto(
        double lat, double lon, String zone_name, String zone_info_url,
        List<RegsSpeciesLimitDto> rules, String disclaimer) {}
    private record RegsStationDto(
        String no_bqma, String hydronyme, double latitude, double longitude, double distance_km) {}
    private record RegsConsumptionDto(
        double lat, double lon, String species, String station_name, double distance_km,
        String size_class, Integer meals_per_month, String fishing_status, String note, String disclaimer) {}
    private record RegsAskRequest(String question) {}
    private record RegsAskResponseDto(String question, String answer, List<String> sources, String disclaimer) {}
}
