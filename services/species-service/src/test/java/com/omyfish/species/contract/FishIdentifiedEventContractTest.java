package com.omyfish.species.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.omyfish.shared.events.FishIdentifiedEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the wire shape RabbitMQEventPublisher actually sends conforms to the
 * AsyncAPI contract shared with the consumers in observation-service and notification-service.
 *
 * Contract source of truth: shared/omyfish-shared-events/asyncapi/fish-identified.yaml
 */
class FishIdentifiedEventContractTest {

    private static final Path SCHEMA_PATH = Path.of(
        "..", "..", "shared", "omyfish-shared-events", "asyncapi", "fish-identified.yaml"
    );

    @Test
    void publishedEventPayloadConformsToAsyncApiSchema() throws IOException {
        JsonSchema schema = loadMessageSchema();

        FishIdentifiedEvent event = new FishIdentifiedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Largemouth Bass",
            0.92,
            List.of(
                new FishIdentifiedEvent.PredictionResult("Largemouth Bass", 0.92, 1),
                new FishIdentifiedEvent.PredictionResult("Smallmouth Bass", 0.05, 2)
            ),
            "observations/2026/08/01/abc123.jpg"
        );

        // Mirrors the ObjectMapper Jackson2JsonMessageConverter builds in RabbitMQConfig:
        // JavaTimeModule registered, timestamps written as ISO-8601 strings.
        ObjectMapper wireMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonNode payload = wireMapper.valueToTree(event);

        Set<ValidationMessage> violations = schema.validate(payload);

        assertThat(violations).as("payload violates fish-identified.yaml: %s", violations).isEmpty();
    }

    private static JsonSchema loadMessageSchema() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode asyncApiDoc = yamlMapper.readTree(Files.readString(SCHEMA_PATH));

        // FishIdentifiedEvent's schema $refs #/components/schemas/PredictionResult, which must
        // resolve against the whole document — so point a root-level $ref at the message schema
        // instead of extracting it in isolation, keeping the sibling schema reachable.
        ObjectNode root = (ObjectNode) asyncApiDoc;
        root.set("$ref", new TextNode("#/components/schemas/FishIdentifiedEvent"));

        return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(root);
    }
}
