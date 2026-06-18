package com.omyfish.observation.adapter.out.storage;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

@Component
public class MinIOStorageAdapter {

    public String store(InputStream data, String contentType) {
        return "observations/" + UUID.randomUUID() + ".jpg";
    }
}
