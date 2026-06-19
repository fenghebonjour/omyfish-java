package com.omyfish.observation.adapter.out.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

@Component
public class MinIOStorageAdapter {

    private final MinioClient minioClient;
    private final String bucket;

    public MinIOStorageAdapter(MinioClient minioClient, @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public String store(InputStream data, String contentType) {
        String objectKey = "observations/" + UUID.randomUUID() + ".jpg";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(data, -1, 10 * 1024 * 1024)
                .contentType(contentType)
                .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store image in MinIO", e);
        }
        return objectKey;
    }
}
