package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface CreateApiKeyUseCase {
    record CreateApiKeyCommand(UUID userId, String name) {}
    record CreateApiKeyResult(UUID keyId, String plainKey, String name) {}

    CreateApiKeyResult createApiKey(CreateApiKeyCommand command);
}
