package com.omyfish.species.adapter.in.web.dto;

import java.util.UUID;

public record IdentifyFishRequest(
    String imageStorageKey,
    int topK,
    UUID observationId,
    UUID userId
) {}
