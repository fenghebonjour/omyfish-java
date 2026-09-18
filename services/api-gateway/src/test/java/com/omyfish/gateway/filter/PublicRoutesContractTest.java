package com.omyfish.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against AuthFilter.PUBLIC_PREFIXES silently drifting from the reviewed, spec-driven
 * list of routes that must not require a JWT (BACKLOG.md item D). Each owning service asserts
 * its own actual endpoints against the same file in its own GatewayPublicRoutesContractTest —
 * api-gateway has no compile-time visibility into those services' controllers, so this shared
 * YAML is what both sides validate against instead of each other's code.
 *
 * Contract source of truth: shared/api-contracts/public-routes.yaml
 */
class PublicRoutesContractTest {

    private static final Path SPEC_PATH = Path.of(
        "..", "..", "shared", "api-contracts", "public-routes.yaml"
    );

    @Test
    void authFilterPublicPrefixes_matchesTheSharedSpecExactly() throws IOException {
        List<String> specPrefixes = loadSpecPrefixes();

        assertThat(AuthFilter.PUBLIC_PREFIXES)
            .as("AuthFilter.PUBLIC_PREFIXES vs shared/api-contracts/public-routes.yaml")
            .containsExactlyInAnyOrderElementsOf(specPrefixes);
    }

    private static List<String> loadSpecPrefixes() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode spec = yamlMapper.readTree(Files.readString(SPEC_PATH));

        List<String> prefixes = new ArrayList<>();
        for (JsonNode entry : spec.get("publicPrefixes")) {
            prefixes.add(entry.get("prefix").asText());
        }
        return prefixes;
    }
}
