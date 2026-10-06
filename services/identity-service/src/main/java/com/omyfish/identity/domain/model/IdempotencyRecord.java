package com.omyfish.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserves a client-supplied Idempotency-Key against one endpoint before the
 * Stripe call runs, then records the result so a retried request with the
 * same key replays it instead of calling Stripe again.
 */
@Entity
@Table(name = "idempotency_keys", schema = "identity")
public class IdempotencyRecord {

    public static final String CHECKOUT = "checkout";
    public static final String REFUND = "refund";

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean completed;

    private String customerId;
    private String subscriptionId;
    private String clientSecret;
    private String checkoutStatus;
    private String refundId;
    private String refundStatus;
    private Long amountCents;

    private Instant createdAt;

    protected IdempotencyRecord() {}

    public static IdempotencyRecord reserve(String idempotencyKey, String endpoint, UUID userId) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.id = UUID.randomUUID();
        r.idempotencyKey = idempotencyKey;
        r.endpoint = endpoint;
        r.userId = userId;
        r.completed = false;
        r.createdAt = Instant.now();
        return r;
    }

    public void completeCheckout(String customerId, String subscriptionId,
                                 String clientSecret, String status) {
        this.customerId = customerId;
        this.subscriptionId = subscriptionId;
        this.clientSecret = clientSecret;
        this.checkoutStatus = status;
        this.completed = true;
    }

    public void completeRefund(String refundId, String status, Long amountCents) {
        this.refundId = refundId;
        this.refundStatus = status;
        this.amountCents = amountCents;
        this.completed = true;
    }

    public UUID getUserId() { return userId; }
    public boolean isCompleted() { return completed; }
    public String getCustomerId() { return customerId; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getClientSecret() { return clientSecret; }
    public String getCheckoutStatus() { return checkoutStatus; }
    public String getRefundId() { return refundId; }
    public String getRefundStatus() { return refundStatus; }
    public Long getAmountCents() { return amountCents; }
}
