package com.omyfish.observation.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

interface ObservationJpaRepository extends JpaRepository<ObservationJpaEntity, UUID> {
    List<ObservationJpaEntity> findByUserId(UUID userId);
    List<ObservationJpaEntity> findByLatitudeIsNotNullAndLongitudeIsNotNull();

    @Query(value = """
        SELECT * FROM observation.observations o
        WHERE ST_DWithin(
            o.location::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radiusMeters
        )
        """, nativeQuery = true)
    List<ObservationJpaEntity> findWithinRadius(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusMeters") double radiusMeters
    );
}
