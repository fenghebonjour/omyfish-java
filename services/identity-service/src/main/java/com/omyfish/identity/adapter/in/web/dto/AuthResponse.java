package com.omyfish.identity.adapter.in.web.dto;

import java.util.UUID;

public record AuthResponse(String token, String refreshToken, UUID userId, String email, String role) {}
