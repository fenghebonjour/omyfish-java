package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface RegisterUseCase {
    record RegisterCommand(String email, String password) {}
    record RegisterResult(UUID userId, String email) {}

    RegisterResult register(RegisterCommand command);
}
