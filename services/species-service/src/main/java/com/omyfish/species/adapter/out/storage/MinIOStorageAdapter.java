package com.omyfish.species.adapter.out.storage;

import com.omyfish.species.domain.port.out.StoragePort;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

@Component
public class MinIOStorageAdapter implements StoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinIOStorageAdapter(
        @Value("${minio.endpoint}") String endpoint,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey,
        @Value("${minio.bucket:fish-images}") String bucket
    ) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }

    @PostConstruct
    public void init() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO bucket: " + bucket, e);
        }
    }

    @Override
    public String store(InputStream data, long size, String contentType, String originalFilename) {
        String ext = (originalFilename != null && originalFilename.contains("."))
            ? originalFilename.substring(originalFilename.lastIndexOf('.'))
            : ".jpg";
        String key = "uploads/" + UUID.randomUUID() + ext;
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to store image in MinIO", e);
        }
        return key;
    }
}
