package com.omyfish.identity.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.omyfish.identity.adapter.in.web.AdminController;
import com.omyfish.identity.adapter.in.web.AuthController;
import com.omyfish.identity.adapter.in.web.BillingController;
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
 * Asserts identity-service's actual registered endpoints against the gateway's public-route
 * whitelist (BACKLOG.md item D) — api-gateway's AuthFilter can't see this module's controllers
 * directly, so both sides validate against the same spec file instead of each other's code.
 * Adding a new endpoint under a whitelisted prefix (e.g. anything under /api/v1/auth/) makes it
 * public automatically; the hardcoded EXPECTED_PUBLIC_PATHS set below must then be updated
 * deliberately, which is the point — it forces a conscious "should this really be public?" check.
 *
 * Contract source of truth: shared/api-contracts/public-routes.yaml
 */
class GatewayPublicRoutesContractTest {

    private static final Path SPEC_PATH = Path.of(
        "..", "..", "shared", "api-contracts", "public-routes.yaml"
    );

    private static final Set<String> EXPECTED_PUBLIC_PATHS = Set.of(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/me",
        "/api/v1/auth/health",
        "/api/v1/billing/webhook"
    );

    @Test
    void identityServiceEndpoints_matchingAPublicPrefix_areExactlyTheReviewedSet() throws IOException {
        List<String> publicPrefixes = loadPublicPrefixesFor("identity-service");

        Set<String> allEndpoints = new HashSet<>();
        allEndpoints.addAll(mappedPaths(AuthController.class));
        allEndpoints.addAll(mappedPaths(BillingController.class));
        allEndpoints.addAll(mappedPaths(AdminController.class));

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
