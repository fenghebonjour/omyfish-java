package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface RefreshTokenUseCase {
    record RefreshResult(String token, String refreshToken, UUID userId, String email, String role) {}

    /** Rotates a valid refresh token into a new access/refresh pair. */
    RefreshResult refresh(String refreshToken);
}
