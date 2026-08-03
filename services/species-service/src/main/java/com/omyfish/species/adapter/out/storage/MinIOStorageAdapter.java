package com.omyfish.species.adapter.out.storage;

import com.omyfish.shared.storage.MinioObjectStorage;
import com.omyfish.species.domain.port.out.StoragePort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinIOStorageAdapter implements StoragePort {

    private static final String KEY_PREFIX = "uploads/";

    private final MinioObjectStorage storage;

    public MinIOStorageAdapter(
        @Value("${minio.endpoint}") String endpoint,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey,
        @Value("${minio.bucket:fish-images}") String bucket
    ) {
        this.storage = MinioObjectStorage.connect(endpoint, accessKey, secretKey, bucket);
    }

    @PostConstruct
    public void init() {
        storage.createBucketIfMissing();
    }

    @Override
    public String store(InputStream data, long size, String contentType, String originalFilename) {
        return storage.store(KEY_PREFIX, data, size, contentType, originalFilename);
    }
}
