CREATE TABLE observation.outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

-- Partial index: the poller only ever scans unpublished rows, and published ones
-- accumulate indefinitely (WEAKNESS_AUDIT.md §2.3 doesn't call for a cleanup job).
CREATE INDEX idx_outbox_events_unpublished ON observation.outbox_events (created_at)
    WHERE published_at IS NULL;
