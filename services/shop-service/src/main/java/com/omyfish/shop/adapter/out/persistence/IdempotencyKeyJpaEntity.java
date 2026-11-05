package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.IdempotencyRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", schema = "shop")
class IdempotencyKeyJpaEntity {

    @Id
    private UUID id;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    private String endpoint;
    @Column(name = "owner_key")
    private String ownerKey;
    private boolean completed;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "checkout_url")
    private String checkoutUrl;
    @Column(name = "created_at")
    private Instant createdAt;

    protected IdempotencyKeyJpaEntity() {}

    static IdempotencyKeyJpaEntity from(IdempotencyRecord r) {
        IdempotencyKeyJpaEntity e = new IdempotencyKeyJpaEntity();
        e.id = r.getId();
        e.idempotencyKey = r.getIdempotencyKey();
        e.endpoint = r.getEndpoint();
        e.ownerKey = r.getOwnerKey();
        e.completed = r.isCompleted();
        e.orderId = r.getOrderId();
        e.checkoutUrl = r.getCheckoutUrl();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    IdempotencyRecord toDomain() {
        return IdempotencyRecord.reconstitute(
            id, idempotencyKey, endpoint, ownerKey, completed, orderId, checkoutUrl, createdAt);
    }
}
