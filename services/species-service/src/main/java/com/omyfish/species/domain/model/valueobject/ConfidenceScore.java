package com.omyfish.species.domain.model.valueobject;

import com.omyfish.shared.domain.ValueObject;

import java.util.Objects;

public final class ConfidenceScore extends ValueObject {

    private static final double UNCERTAIN_THRESHOLD = 0.30;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;

    private final double value;

    private ConfidenceScore(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0, was: " + value);
        }
        this.value = value;
    }

    public static ConfidenceScore of(double value) {
        return new ConfidenceScore(value);
    }

    public double getValue() { return value; }

    public boolean isUncertain() { return value < UNCERTAIN_THRESHOLD; }
    public boolean isHighConfidence() { return value >= HIGH_CONFIDENCE_THRESHOLD; }

    public String asPercent() {
        return String.format("%.1f%%", value * 100);
    }

    @Override
    protected boolean sameValueAs(ValueObject other) {
        if (!(other instanceof ConfidenceScore that)) return false;
        return Double.compare(value, that.value) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfidenceScore that)) return false;
        return Double.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return asPercent(); }
}
