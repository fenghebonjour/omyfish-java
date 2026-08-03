package com.omyfish.observation.adapter.out.gis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExifExtractorAdapterTest {

    private final ExifExtractorAdapter adapter = new ExifExtractorAdapter();

    @Test
    void unreadableImage_degradesToEmptyMetadata() {
        var extraction = adapter.extract(
            new ByteArrayInputStream("not an image".getBytes(StandardCharsets.UTF_8)));

        assertThat(extraction.metadata().capturedAt()).isNull();
        assertThat(extraction.gps().isPresent()).isFalse();
    }
}
