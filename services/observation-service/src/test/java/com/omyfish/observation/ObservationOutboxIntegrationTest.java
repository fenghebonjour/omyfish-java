package com.omyfish.observation;

import com.omyfish.observation.adapter.out.messaging.OutboxPublisherJob;
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaEntity;
import com.omyfish.observation.adapter.out.persistence.OutboxEventJpaRepository;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase.CreateCommand;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the outbox pattern (WEAKNESS_AUDIT.md §2.3) against a real Postgres and a
 * real RabbitMQ broker: the observation write and the outbox write land in one transaction, and
 * the separate poller is the only thing that ever touches the broker.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class ObservationOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"));

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired CreateObservationUseCase createObservationUseCase;
    @Autowired OutboxEventJpaRepository outboxRepository;
    @Autowired OutboxPublisherJob publisherJob;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired TopicExchange observationsExchange;
    @Autowired RabbitTemplate rabbitTemplate;

    @Test
    void create_writesObservationAndOutboxRowInTheSameTransaction() {
        Observation created = createObservationUseCase.create(command("Pike"));

        assertThat(created.getId()).isNotNull();
        List<OutboxEventJpaEntity> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(pending)
            .anySatisfy(e -> {
                assertThat(e.getRoutingKey()).isEqualTo("observation.created");
                assertThat(e.getPayload()).contains(created.getId().toString());
                assertThat(e.getPublishedAt()).isNull();
            });
    }

    @Test
    void publisherJob_sendsPendingRowToRabbitAndMarksItPublished() {
        Queue testQueue = new Queue("test.observation-outbox", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(observationsExchange).with("observation.created"));

        Observation created = createObservationUseCase.create(command("Muskellunge"));
        publisherJob.publishPending();

        var message = rabbitTemplate.receive(testQueue.getName(), 5000);
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody())).contains(created.getId().toString());

        boolean stillPending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc().stream()
            .anyMatch(e -> e.getPayload().contains(created.getId().toString()));
        assertThat(stillPending).isFalse();
    }

    private CreateCommand command(String speciesName) {
        return new CreateCommand(UUID.randomUUID(), speciesName, "Scientificus name",
            0.9, "key.jpg", null, null, null);
    }
}
