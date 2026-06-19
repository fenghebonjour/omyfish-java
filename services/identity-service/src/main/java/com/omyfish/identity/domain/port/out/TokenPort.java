package com.omyfish.identity.domain.port.out;

import java.util.UUID;

public interface TokenPort {
    String issue(UUID userId, String email, String role);
}
