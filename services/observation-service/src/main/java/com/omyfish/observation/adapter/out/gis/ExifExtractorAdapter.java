package com.omyfish.observation.adapter.out.gis;

import com.omyfish.observation.domain.model.valueobject.ExifMetadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ExifExtractorAdapter {

    public ExifMetadata extract(InputStream imageStream) {
        return ExifMetadata.empty();
    }
}
