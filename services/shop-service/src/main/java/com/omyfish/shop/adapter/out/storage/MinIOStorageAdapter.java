package com.omyfish.shop.adapter.out.storage;

import com.omyfish.shop.domain.model.valueobject.StoredImage;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

// Same shape as species-service's MinIOStorageAdapter (the one real precedent for this pattern
// in the repo — observation-service has an equivalent class that's never actually wired up).
@Component
public class MinIOStorageAdapter implements ImageStoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinIOStorageAdapter(
        @Value("${minio.endpoint}") String endpoint,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey,
        @Value("${minio.bucket:product-images}") String bucket
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
        String key = "products/" + UUID.randomUUID() + ext;
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

    @Override
    public StoredImage load(String key) {
        try {
            GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build());
            String contentType = response.headers().get("Content-Type");
            return new StoredImage(response, contentType != null ? contentType : "application/octet-stream");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load image from MinIO: " + key, e);
        }
    }
}
