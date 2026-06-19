package com.omyfish.identity.adapter.in.web.dto;

import java.util.UUID;

public record AuthResponse(String token, UUID userId, String email, String role) {}
