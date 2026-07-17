package com.omyfish.observation.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface ObservationJpaRepository extends JpaRepository<ObservationJpaEntity, UUID> {
    List<ObservationJpaEntity> findByUserId(UUID userId);
    List<ObservationJpaEntity> findByLatitudeIsNotNullAndLongitudeIsNotNull();
}
