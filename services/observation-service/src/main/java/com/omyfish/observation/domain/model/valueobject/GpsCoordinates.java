package com.omyfish.observation.domain.model.valueobject;

public record GpsCoordinates(Double latitude, Double longitude) {

    public GpsCoordinates {
        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, was: " + latitude);
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, was: " + longitude);
        }
    }

    public boolean isPresent() {
        return latitude != null && longitude != null;
    }

    public String toWKT() {
        return String.format("POINT(%f %f)", longitude, latitude);
    }

    public static GpsCoordinates of(Double latitude, Double longitude) {
        return new GpsCoordinates(latitude, longitude);
    }

    public static GpsCoordinates unknown() {
        return new GpsCoordinates(null, null);
    }
}
