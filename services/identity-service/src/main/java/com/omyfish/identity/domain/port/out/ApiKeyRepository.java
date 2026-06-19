package com.omyfish.identity.domain.port.out;

import com.omyfish.identity.domain.model.ApiKey;

import java.util.Optional;

public interface ApiKeyRepository {
    ApiKey save(ApiKey apiKey);
    Optional<ApiKey> findByKeyHash(String keyHash);
}
