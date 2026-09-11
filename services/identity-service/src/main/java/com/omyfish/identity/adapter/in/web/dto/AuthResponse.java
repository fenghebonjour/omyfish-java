package com.omyfish.identity.adapter.in.web.dto;

import java.util.UUID;

// No refreshToken here — it travels only as an httpOnly cookie now
// (BACKLOG.md item G, WEAKNESS_AUDIT.md §1.3).
public record AuthResponse(String token, UUID userId, String email, String role) {}
