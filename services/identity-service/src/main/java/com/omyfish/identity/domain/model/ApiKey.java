package com.omyfish.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", schema = "identity")
public class ApiKey {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private Instant expiresAt;
    private Instant createdAt;

    protected ApiKey() {}

    public static ApiKey create(UUID userId, String keyHash, String name) {
        ApiKey k = new ApiKey();
        k.id = UUID.randomUUID();
        k.userId = userId;
        k.keyHash = keyHash;
        k.name = name;
        k.createdAt = Instant.now();
        return k;
    }

    public UUID getId() { return id; }
    public String getKeyHash() { return keyHash; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public Instant getExpiresAt() { return expiresAt; }
}
