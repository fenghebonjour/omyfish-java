package com.omyfish.observation.adapter.out.persistence;

import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Backed by the same postgis/postgis image docker-compose.yml uses for the real stack, so
// ST_DWithin behaves identically to production (a plain postgres image lacks the PostGIS
// extension the V1 migration's `CREATE EXTENSION postgis` and `location` column need).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ObservationRepositoryAdapterRadiusSearchIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ObservationJpaRepository jpaRepository;

    private ObservationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ObservationRepositoryAdapter(jpaRepository);
    }

    @Test
    void findWithinRadius_returnsPointsInsideRadius_excludesPointsOutside() {
        Observation montreal = repository.save(observationAt(45.5017, -73.5673));
        Observation toronto = repository.save(observationAt(43.6532, -79.3832)); // ~500km away

        List<Observation> results = repository.findWithinRadius(45.5017, -73.5673, 10_000); // 10km

        assertThat(results).extracting(Observation::getId)
            .contains(montreal.getId())
            .doesNotContain(toronto.getId());
    }

    private static Observation observationAt(double lat, double lng) {
        return Observation.create(
            UUID.randomUUID(), "Walleye", "Sander vitreus", 0.9, "key.jpg",
            GpsCoordinates.of(lat, lng), null, null
        );
    }
}
