package com.omyfish.shared.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO bucket wrapper shared by the services that upload fish images: bucket
 * creation, random object keys and the {@code putObject} call with its defaults.
 */
public class MinioObjectStorage {

    private static final long DEFAULT_PART_SIZE = 10L * 1024 * 1024;
    private static final String DEFAULT_EXTENSION = ".jpg";
    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public static MinioObjectStorage connect(String endpoint, String accessKey, String secretKey, String bucket) {
        return new MinioObjectStorage(
            MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(), bucket);
    }

    public void createBucketIfMissing() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new ObjectStorageException("Failed to initialize MinIO bucket: " + bucket, e);
        }
    }

    /**
     * Stores an object under {@code keyPrefix} with a random name, keeping the
     * original file extension when one is available.
     *
     * @param size the stream length, or a negative value when it is unknown
     */
    public String store(String keyPrefix, InputStream data, long size, String contentType, String originalFilename) {
        String key = keyPrefix + UUID.randomUUID() + extensionOf(originalFilename);
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(data, size >= 0 ? size : -1, size >= 0 ? -1 : DEFAULT_PART_SIZE)
                .contentType(contentType != null ? contentType : DEFAULT_CONTENT_TYPE)
                .build());
        } catch (Exception e) {
            throw new ObjectStorageException("Failed to store image in MinIO: " + key, e);
        }
        return key;
    }

    private static String extensionOf(String originalFilename) {
        return originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf('.'))
            : DEFAULT_EXTENSION;
    }
}
