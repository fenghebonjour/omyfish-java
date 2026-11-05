CREATE TABLE shop.order_event_ledger (
    id               UUID        PRIMARY KEY,
    event_id         VARCHAR(64) NOT NULL,
    order_id         UUID        NOT NULL,
    product_id       UUID        NOT NULL,
    product_name     VARCHAR(255) NOT NULL,
    quantity         INT         NOT NULL,
    unit_price_cents BIGINT      NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One event carries multiple lines (one per product) — dedup is per (event, product), not just
-- per event, so a redelivered OrderPlacedEvent doesn't insert duplicate ledger rows.
CREATE UNIQUE INDEX idx_order_event_ledger_event_product
    ON shop.order_event_ledger (event_id, product_id);

CREATE INDEX idx_order_event_ledger_order_id
    ON shop.order_event_ledger (order_id);
