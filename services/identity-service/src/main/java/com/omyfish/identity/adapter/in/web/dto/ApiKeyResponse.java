package com.omyfish.identity.adapter.in.web.dto;

import java.util.UUID;

public record ApiKeyResponse(UUID keyId, String key, String name) {}
