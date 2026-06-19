package com.omyfish.identity.adapter.in.web.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email) {}
