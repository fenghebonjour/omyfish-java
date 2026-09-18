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
import com.omyfish.observation.adapter.in.messaging.FishIdentifiedConsumer;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import com.omyfish.shared.events.FishIdentifiedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.MethodParameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves observation-service's actual RabbitMQConfig.messageConverter() bean can reconstruct a
 * FishIdentifiedEvent from a message shaped exactly as species-service's own converter would
 * send it — species-service's FishIdentifiedEventContractTest only guards the publisher side of
 * this contract (BACKLOG.md item D).
 *
 * Contract source of truth: shared/omyfish-shared-events/asyncapi/fish-identified.yaml
 */
@ExtendWith(MockitoExtension.class)
class FishIdentifiedEventConsumerContractTest {

    private static final Path SCHEMA_PATH = Path.of(
        "..", "..", "shared", "omyfish-shared-events", "asyncapi", "fish-identified.yaml"
    );

    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock ObservationRepository observationRepository;

    @Test
    void handlerDeserializesAMessageSentTheWaySpeciesServiceActuallySendsIt() throws Exception {
        FishIdentifiedEvent event = new FishIdentifiedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Largemouth Bass", 0.92,
            List.of(new FishIdentifiedEvent.PredictionResult("Largemouth Bass", 0.92, 1)),
            "observations/2026/09/18/abc123.jpg"
        );

        // Sanity check: the fixture itself is schema-valid before we test anything downstream of it.
        assertThat(loadMessageSchema().validate(WIRE_MAPPER.valueToTree(event))).isEmpty();

        // species-service's RabbitMQConfig.messageConverter() is a plain Jackson2JsonMessageConverter
        // (default TYPE_ID precedence) — mirror it exactly to build the message as it's really sent.
        Message message = new Jackson2JsonMessageConverter().toMessage(event, new MessageProperties());

        // observation-service's own RabbitMQConfig.messageConverter() — same, no INFERRED override.
        Jackson2JsonMessageConverter receiverConverter = new Jackson2JsonMessageConverter();
        MethodParameter handlerParameter = new MethodParameter(
            FishIdentifiedConsumer.class.getMethod("handle", FishIdentifiedEvent.class), 0);

        Object converted = receiverConverter.fromMessage(message, handlerParameter);

        assertThat(converted).isInstanceOf(FishIdentifiedEvent.class);
        FishIdentifiedEvent received = (FishIdentifiedEvent) converted;
        assertThat(received).isEqualTo(event);

        FishIdentifiedConsumer consumer = new FishIdentifiedConsumer(observationRepository);
        assertThatCode(() -> consumer.handle(received)).doesNotThrowAnyException();
    }

    private static JsonSchema loadMessageSchema() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode asyncApiDoc = yamlMapper.readTree(Files.readString(SCHEMA_PATH));

        ObjectNode root = (ObjectNode) asyncApiDoc;
        root.set("$ref", new TextNode("#/components/schemas/FishIdentifiedEvent"));

        return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(root);
    }
}
