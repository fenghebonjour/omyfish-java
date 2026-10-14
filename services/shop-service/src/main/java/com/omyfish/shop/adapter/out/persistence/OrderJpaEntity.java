package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "shop")
class OrderJpaEntity {

    @Id
    private UUID id;
    private UUID userId;
    private String guestEmail;

    @ElementCollection
    @CollectionTable(name = "order_lines", schema = "shop", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_index")
    private List<OrderLineEmbeddable> lines = new ArrayList<>();

    private long totalCents;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String stripeSessionId;
    private String stripePaymentIntentId;
    private Instant createdAt;
    private Instant paidAt;
    private Instant returnRequestedAt;
    private Instant returnedAt;
    @Column(length = 2000)
    private String returnReason;

    protected OrderJpaEntity() {}

    static OrderJpaEntity from(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.id = order.getId();
        e.userId = order.getUserId();
        e.guestEmail = order.getGuestEmail();
        e.lines = order.getLines().stream().map(OrderLineEmbeddable::from).toList();
        e.totalCents = order.getTotalCents();
        e.status = order.getStatus();
        e.stripeSessionId = order.getStripeSessionId();
        e.stripePaymentIntentId = order.getStripePaymentIntentId();
        e.createdAt = order.getCreatedAt();
        e.paidAt = order.getPaidAt();
        e.returnRequestedAt = order.getReturnRequestedAt();
        e.returnedAt = order.getReturnedAt();
        e.returnReason = order.getReturnReason();
        return e;
    }

    Order toDomain() {
        return Order.reconstitute(
            id, userId, guestEmail, lines.stream().map(OrderLineEmbeddable::toDomain).toList(), totalCents, status,
            stripeSessionId, stripePaymentIntentId, createdAt, paidAt, returnRequestedAt, returnedAt, returnReason);
    }
}
