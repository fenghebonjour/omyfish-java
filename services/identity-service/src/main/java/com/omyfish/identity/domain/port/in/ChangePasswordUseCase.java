package com.omyfish.identity.domain.port.in;

import java.util.UUID;

public interface ChangePasswordUseCase {
    record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {}

    /** Verifies currentPassword against the stored hash, then saves newPassword's hash. */
    void changePassword(ChangePasswordCommand command);
}
