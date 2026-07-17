package com.omyfish.species.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConfidenceScoreTest {

    @Test
    void rejectsValuesOutsideZeroToOne() {
        assertThatThrownBy(() -> ConfidenceScore.of(-0.01))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfidenceScore.of(1.01))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> ConfidenceScore.of(0.0)).doesNotThrowAnyException();
        assertThatCode(() -> ConfidenceScore.of(1.0)).doesNotThrowAnyException();
    }

    @Test
    void uncertainBelowThreshold() {
        assertThat(ConfidenceScore.of(0.29).isUncertain()).isTrue();
        assertThat(ConfidenceScore.of(0.30).isUncertain()).isFalse();
    }

    @Test
    void highConfidenceAtOrAboveThreshold() {
        assertThat(ConfidenceScore.of(0.84).isHighConfidence()).isFalse();
        assertThat(ConfidenceScore.of(0.85).isHighConfidence()).isTrue();
    }

    @Test
    void equalityByValue() {
        assertThat(ConfidenceScore.of(0.5)).isEqualTo(ConfidenceScore.of(0.5));
        assertThat(ConfidenceScore.of(0.5)).isNotEqualTo(ConfidenceScore.of(0.6));
    }
}
