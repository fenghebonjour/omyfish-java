package com.omyfish.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Marks a Stripe webhook event id as already acted on, so a redelivery is skipped. */
@Entity
@Table(name = "processed_webhook_events", schema = "identity")
public class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    private Instant processedAt;

    protected ProcessedWebhookEvent() {}

    public static ProcessedWebhookEvent of(String eventId) {
        ProcessedWebhookEvent e = new ProcessedWebhookEvent();
        e.eventId = eventId;
        e.processedAt = Instant.now();
        return e;
    }

    public String getEventId() { return eventId; }
}
