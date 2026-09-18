package com.omyfish.species.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.omyfish.species.adapter.in.web.BiteScoreController;
import com.omyfish.species.adapter.in.web.IdentificationController;
import com.omyfish.species.adapter.in.web.RegsController;
import com.omyfish.species.adapter.in.web.SpeciesController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts species-service's actual registered endpoints against the gateway's public-route
 * whitelist (BACKLOG.md item D). The whole /api/v1/species surface is intentionally public
 * (WEAKNESS_AUDIT.md §1.2), so EXPECTED_PUBLIC_PATHS below is effectively "every endpoint this
 * service exposes" — a new endpoint that shouldn't be anonymous still needs to be added here
 * deliberately, which is the point.
 *
 * Contract source of truth: shared/api-contracts/public-routes.yaml
 */
class GatewayPublicRoutesContractTest {

    private static final Path SPEC_PATH = Path.of(
        "..", "..", "shared", "api-contracts", "public-routes.yaml"
    );

    private static final Set<String> EXPECTED_PUBLIC_PATHS = Set.of(
        "/api/v1/species",
        "/api/v1/species/{scientificName}",
        "/api/v1/species/identify",
        "/api/v1/species/regs/limits",
        "/api/v1/species/regs/zones/geojson",
        "/api/v1/species/regs/consumption/stations",
        "/api/v1/species/regs/consumption",
        "/api/v1/species/regs/ask",
        "/api/v1/species/bite-score/forecast",
        "/api/v1/species/bite-score/today"
    );

    @Test
    void speciesServiceEndpoints_matchingAPublicPrefix_areExactlyTheReviewedSet() throws IOException {
        List<String> publicPrefixes = loadPublicPrefixesFor("species-service");

        Set<String> allEndpoints = new HashSet<>();
        allEndpoints.addAll(mappedPaths(SpeciesController.class));
        allEndpoints.addAll(mappedPaths(IdentificationController.class));
        allEndpoints.addAll(mappedPaths(RegsController.class));
        allEndpoints.addAll(mappedPaths(BiteScoreController.class));

        Set<String> actualPublic = allEndpoints.stream()
            .filter(path -> publicPrefixes.stream().anyMatch(path::startsWith))
            .collect(java.util.stream.Collectors.toSet());

        assertThat(actualPublic)
            .as("endpoints matching a public prefix from public-routes.yaml")
            .isEqualTo(EXPECTED_PUBLIC_PATHS);
    }

    private static List<String> loadPublicPrefixesFor(String service) throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode spec = yamlMapper.readTree(Files.readString(SPEC_PATH));

        List<String> prefixes = new ArrayList<>();
        for (JsonNode entry : spec.get("publicPrefixes")) {
            if (service.equals(entry.get("service").asText())) {
                prefixes.add(entry.get("prefix").asText());
            }
        }
        return prefixes;
    }

    private static Set<String> mappedPaths(Class<?> controllerClass) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
        String basePath = classMapping.value().length > 0 ? classMapping.value()[0] : "";

        Set<String> paths = new HashSet<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (methodMapping == null) continue;
            if (methodMapping.value().length == 0) {
                paths.add(basePath);
            } else {
                for (String subPath : methodMapping.value()) {
                    paths.add(basePath + subPath);
                }
            }
        }
        return paths;
    }
}
