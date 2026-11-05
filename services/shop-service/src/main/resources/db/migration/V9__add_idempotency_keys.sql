-- Idempotency cache for /checkout. A client-supplied Idempotency-Key reserves a row before
-- reserveOrder() and the payment-processor call run; the row is then completed with the
-- resulting order/checkout-url so a retried request with the same key replays it instead of
-- reserving stock and creating an order a second time. The unique constraint also catches two
-- concurrent requests racing on the same key. Same shape as identity.idempotency_keys
-- (V4__add_idempotency_keys.sql), adapted to Omyshop's order/checkout-url result instead of a
-- subscription/customer id.
CREATE TABLE IF NOT EXISTS shop.idempotency_keys (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    endpoint        VARCHAR(20) NOT NULL,
    owner_key       VARCHAR(255) NOT NULL,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    order_id        UUID,
    checkout_url    VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT idempotency_keys_key_endpoint_unique UNIQUE (idempotency_key, endpoint)
);
