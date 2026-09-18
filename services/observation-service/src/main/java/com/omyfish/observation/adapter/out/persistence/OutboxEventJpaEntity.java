package com.omyfish.observation.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "observation")
public class OutboxEventJpaEntity {

    @Id
    private UUID id;
    private String eventType;
    private String exchange;
    private String routingKey;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {}

    public OutboxEventJpaEntity(String eventType, String exchange, String routingKey, String payload) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
