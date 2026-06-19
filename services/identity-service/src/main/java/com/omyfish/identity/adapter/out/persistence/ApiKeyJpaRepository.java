package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ApiKeyJpaRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);
}
