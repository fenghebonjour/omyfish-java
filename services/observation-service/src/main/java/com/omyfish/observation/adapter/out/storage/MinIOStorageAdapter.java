package com.omyfish.observation.adapter.out.storage;

import com.omyfish.shared.storage.MinioObjectStorage;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinIOStorageAdapter {

    private static final String KEY_PREFIX = "observations/";

    private final MinioObjectStorage storage;

    public MinIOStorageAdapter(MinioClient minioClient, @Value("${minio.bucket}") String bucket) {
        this.storage = new MinioObjectStorage(minioClient, bucket);
    }

    public String store(InputStream data, String contentType) {
        return storage.store(KEY_PREFIX, data, -1, contentType, null);
    }
}
