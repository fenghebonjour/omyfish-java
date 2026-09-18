package com.omyfish.observation.contract;

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
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaEntity;
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaRepository;
import com.omyfish.observation.adapter.out.persistence.OutboxEventPublisher;
import com.omyfish.observation.domain.event.ObservationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Validates that the wire shape OutboxEventPublisher actually writes to the outbox table (and
 * that OutboxPublisherJob later sends byte-for-byte to RabbitMQ) conforms to the AsyncAPI
 * contract shared with notification-service.
 *
 * Contract source of truth: shared/omyfish-shared-events/asyncapi/observation-created.yaml
 */
@ExtendWith(MockitoExtension.class)
class ObservationCreatedEventContractTest {

    private static final Path SCHEMA_PATH = Path.of(
        "..", "..", "shared", "omyfish-shared-events", "asyncapi", "observation-created.yaml"
    );

    @Mock OutboxEventJpaRepository outboxRepository;

    // Mirrors Spring Boot's autoconfigured ObjectMapper (JavaTimeModule registered,
    // WRITE_DATES_AS_TIMESTAMPS disabled) — the exact bean OutboxEventPublisher gets injected in
    // production.
    private final ObjectMapper wireMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void outboxPayloadConformsToAsyncApiSchema_withGpsLocation() throws IOException {
        assertPublishedPayloadConformsToSchema(new ObservationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "Largemouth Bass",
            45.5017, -73.5673, "observations/2026/09/18/abc123.jpg", Instant.now()
        ));
    }

    @Test
    void outboxPayloadConformsToAsyncApiSchema_withoutGpsLocation() throws IOException {
        assertPublishedPayloadConformsToSchema(new ObservationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "Northern Pike",
            null, null, "observations/2026/09/18/def456.jpg", Instant.now()
        ));
    }

    private void assertPublishedPayloadConformsToSchema(ObservationCreatedEvent event) throws IOException {
        JsonSchema schema = loadMessageSchema();
        OutboxEventPublisher publisher = new OutboxEventPublisher(outboxRepository, wireMapper);

        publisher.publish(event);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepository).save(captor.capture());

        JsonNode payload = wireMapper.readTree(captor.getValue().getPayload());
        Set<ValidationMessage> violations = schema.validate(payload);

        assertThat(violations).as("payload violates observation-created.yaml: %s", violations).isEmpty();
    }

    private static JsonSchema loadMessageSchema() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode asyncApiDoc = yamlMapper.readTree(Files.readString(SCHEMA_PATH));

        ObjectNode root = (ObjectNode) asyncApiDoc;
        root.set("$ref", new TextNode("#/components/schemas/ObservationCreatedEvent"));

        return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(root);
    }
}
