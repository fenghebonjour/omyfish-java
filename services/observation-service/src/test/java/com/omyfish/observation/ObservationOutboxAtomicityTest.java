package com.omyfish.observation;

import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase.CreateCommand;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the actual guarantee the outbox is for (WEAKNESS_AUDIT.md §2.3): if the outbox write
 * fails, the observation write must roll back with it rather than leaving a saved observation
 * with no corresponding event. Forces that failure with a {@code @Primary} test
 * {@code EventPublisherPort} instead of literally crashing the process mid-transaction.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class ObservationOutboxAtomicityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class FailingPublisherConfig {
        @Bean
        @Primary
        EventPublisherPort failingEventPublisher() {
            return event -> { throw new RuntimeException("simulated outbox write failure"); };
        }
    }

    @Autowired CreateObservationUseCase createObservationUseCase;
    @Autowired ObservationRepository repository;

    @Test
    void create_rollsBackObservationWhenOutboxWriteFails() {
        UUID userId = UUID.randomUUID();
        CreateCommand cmd = new CreateCommand(userId, "Pike", "Esox lucius", 0.9, "key.jpg", null, null, null);

        assertThatThrownBy(() -> createObservationUseCase.create(cmd))
            .hasMessageContaining("simulated outbox write failure");

        assertThat(repository.findByUserId(userId)).isEmpty();
    }
}
