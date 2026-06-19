package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.ApiKey;
import com.omyfish.identity.domain.port.out.ApiKeyRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;

    public ApiKeyRepositoryAdapter(ApiKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        return jpa.save(apiKey);
    }

    @Override
    public Optional<ApiKey> findByKeyHash(String keyHash) {
        return jpa.findByKeyHash(keyHash);
    }
}
