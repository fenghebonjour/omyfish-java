package com.omyfish.shop.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserves a client-supplied Idempotency-Key against one endpoint before checkout runs, then
 * records the result so a retried request with the same key replays it instead of reserving
 * stock and creating an order a second time — same role as identity-service's
 * IdempotencyRecord for its billing checkout, adapted to Omyshop's order/checkout-url shape
 * instead of subscription/customer ids.
 */
public class IdempotencyRecord {

    public static final String CHECKOUT = "checkout";

    private final UUID id;
    private final String idempotencyKey;
    private final String endpoint;
    private final String ownerKey;
    private boolean completed;
    private UUID orderId;
    private String checkoutUrl;
    private final Instant createdAt;

    private IdempotencyRecord(
        UUID id, String idempotencyKey, String endpoint, String ownerKey, boolean completed,
        UUID orderId, String checkoutUrl, Instant createdAt
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.endpoint = endpoint;
        this.ownerKey = ownerKey;
        this.completed = completed;
        this.orderId = orderId;
        this.checkoutUrl = checkoutUrl;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord reserve(String idempotencyKey, String endpoint, String ownerKey) {
        return new IdempotencyRecord(
            UUID.randomUUID(), idempotencyKey, endpoint, ownerKey, false, null, null, Instant.now());
    }

    public static IdempotencyRecord reconstitute(
        UUID id, String idempotencyKey, String endpoint, String ownerKey, boolean completed,
        UUID orderId, String checkoutUrl, Instant createdAt
    ) {
        return new IdempotencyRecord(id, idempotencyKey, endpoint, ownerKey, completed, orderId, checkoutUrl, createdAt);
    }

    public void completeCheckout(UUID orderId, String checkoutUrl) {
        this.orderId = orderId;
        this.checkoutUrl = checkoutUrl;
        this.completed = true;
    }

    /** True once this reservation has sat uncompleted for longer than maxAge. */
    public boolean isStale(Duration maxAge) {
        return createdAt.isBefore(Instant.now().minus(maxAge));
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getEndpoint() { return endpoint; }
    public String getOwnerKey() { return ownerKey; }
    public boolean isCompleted() { return completed; }
    public UUID getOrderId() { return orderId; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
