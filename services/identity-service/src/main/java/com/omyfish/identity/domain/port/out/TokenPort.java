package com.omyfish.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

public interface TokenPort {
    String issue(UUID userId, String email, String role);

    String issueRefresh(UUID userId);

    /** Returns the user id if the token is a valid, unexpired refresh token. */
    Optional<UUID> validateRefresh(String token);

    /** Returns the user id if the token is a valid, unexpired access token. */
    Optional<UUID> validateAccess(String token);
}
