-- Dedup key for RabbitMQ redelivery — ObservationCreatedConsumer used to insert a Notification
-- on every delivery with no idempotency check, so a redelivery (guaranteed eventually under
-- at-least-once + retry) created a duplicate row (BACKLOG.md item G, WEAKNESS_AUDIT.md §2.4,
-- matching omyfish-dotnet's identical Notification.SourceEventId fix). Nullable + partial unique
-- index so this is safe to add without backfilling existing rows.

ALTER TABLE notification.notifications
    ADD COLUMN source_event_id VARCHAR(64);

CREATE UNIQUE INDEX idx_notifications_source_event_id
    ON notification.notifications (source_event_id)
    WHERE source_event_id IS NOT NULL;
