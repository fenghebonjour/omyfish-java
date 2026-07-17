package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface GetCurrentUserUseCase {
    record CurrentUser(UUID userId, String email, String role) {}

    CurrentUser me(String accessToken);
}
