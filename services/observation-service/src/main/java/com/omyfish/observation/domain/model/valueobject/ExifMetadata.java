package com.omyfish.observation.domain.model.valueobject;

import java.time.Instant;

public record ExifMetadata(
    Instant capturedAt,
    String cameraModel,
    Integer imageWidth,
    Integer imageHeight,
    Double focalLength,
    Double aperture,
    Integer iso
) {
    public static ExifMetadata empty() {
        return new ExifMetadata(null, null, null, null, null, null, null);
    }
}
