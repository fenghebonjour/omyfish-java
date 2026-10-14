package com.omyfish.shop.adapter.out.storage;

import com.omyfish.shop.domain.model.valueobject.StoredImage;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// Same shape as species-service's MinIOStorageAdapter (the one real precedent for this pattern
// in the repo — observation-service has an equivalent class that's never actually wired up).
@Component
public class MinIOStorageAdapter implements ImageStoragePort {

    private final MinioClient minioClient;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

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

    // Deferred to first actual upload rather than @PostConstruct — MinIO being unreachable at
    // startup must only fail an upload attempt, never crash the whole service (same philosophy
    // as StripeCheckoutAdapter.isConfigured() never failing app startup over a missing key).
    private void ensureBucketExists() {
        if (bucketReady.get()) return;
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO bucket: " + bucket, e);
        }
    }

    @Override
    public String store(InputStream data, long size, String contentType, String originalFilename) {
        ensureBucketExists();
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
