package com.omyfish.shop.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_event_ledger", schema = "shop")
public class OrderEventLedgerJpaEntity {

    @Id
    private UUID id;
    @Column(name = "event_id")
    private String eventId;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_name")
    private String productName;
    private int quantity;
    @Column(name = "unit_price_cents")
    private long unitPriceCents;
    @Column(name = "received_at")
    private Instant receivedAt;

    protected OrderEventLedgerJpaEntity() {}

    public OrderEventLedgerJpaEntity(
        String eventId, UUID orderId, UUID productId, String productName, int quantity, long unitPriceCents
    ) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.receivedAt = Instant.now();
    }
}
