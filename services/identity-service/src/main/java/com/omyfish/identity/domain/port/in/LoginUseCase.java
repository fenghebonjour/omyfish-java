package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface LoginUseCase {
    record LoginCommand(String email, String password) {}
    record LoginResult(String token, String refreshToken, UUID userId, String email, String role) {}

    LoginResult login(LoginCommand command);
}
