CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    type        VARCHAR(64) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    is_read     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id
    ON notification.notifications (user_id);

CREATE INDEX idx_notifications_user_unread
    ON notification.notifications (user_id, is_read)
    WHERE is_read = FALSE;
