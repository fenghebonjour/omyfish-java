package com.omyfish.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "identity")
public class User {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    private Instant createdAt;
    private Instant updatedAt;

    protected User() {}

    public static User create(String email, String passwordHash, String role) {
        return create(email, passwordHash, role, null);
    }

    public static User create(String email, String passwordHash, String role, String displayName) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.email = email;
        u.passwordHash = passwordHash;
        u.role = role;
        u.displayName = displayName;
        u.active = true;
        u.createdAt = Instant.now();
        u.updatedAt = Instant.now();
        return u;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
