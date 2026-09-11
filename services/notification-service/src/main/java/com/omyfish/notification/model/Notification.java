package com.omyfish.notification.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    private String body;

    @Column(nullable = false)
    private boolean isRead;

    @Column(nullable = false)
    private Instant createdAt;

    // Dedup key for RabbitMQ redelivery — the publishing integration event's own eventId
    // (BACKLOG.md item G, WEAKNESS_AUDIT.md §2.4). Null for notifications created before this
    // column existed or through any path that doesn't have a source event.
    @Column(name = "source_event_id")
    private String sourceEventId;

    protected Notification() {}

    public Notification(UUID userId, String type, String title, String body) {
        this(userId, type, title, body, null);
    }

    public Notification(UUID userId, String type, String title, String body, String sourceEventId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.isRead = false;
        this.createdAt = Instant.now();
        this.sourceEventId = sourceEventId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRead() { return isRead; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSourceEventId() { return sourceEventId; }

    public void markRead() { this.isRead = true; }
}
