package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

    // EAGER: every caller of toDomain() needs lines immediately, and several of them (e.g.
    // GetOrderUseCase) run outside any @Transactional boundary, so a lazy collection here would
    // throw LazyInitializationException once the loading session closes.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", schema = "shop", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_index")
    private List<OrderLineEmbeddable> lines = new ArrayList<>();

    private long totalCents;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String stripeSessionId;
    private String stripePaymentIntentId;
    private String paymentProcessor;
    private Instant createdAt;
    private Instant paidAt;
    private Instant returnRequestedAt;
    private Instant returnedAt;
    @Column(length = 2000)
    private String returnReason;

    // Flat columns rather than @Embedded: an all-null embeddable's null-vs-empty-object
    // behavior on read varies by Hibernate version, and shippingLine1 == null is an
    // unambiguous "no address yet" marker (set together with paidAt, never independently).
    private String shippingRecipientName;
    private String shippingLine1;
    private String shippingLine2;
    private String shippingCity;
    private String shippingState;
    private String shippingPostalCode;
    private String shippingCountry;

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
        e.paymentProcessor = order.getPaymentProcessor();
        e.createdAt = order.getCreatedAt();
        e.paidAt = order.getPaidAt();
        e.returnRequestedAt = order.getReturnRequestedAt();
        e.returnedAt = order.getReturnedAt();
        e.returnReason = order.getReturnReason();
        ShippingAddress shippingAddress = order.getShippingAddress();
        if (shippingAddress != null) {
            e.shippingRecipientName = shippingAddress.recipientName();
            e.shippingLine1 = shippingAddress.line1();
            e.shippingLine2 = shippingAddress.line2();
            e.shippingCity = shippingAddress.city();
            e.shippingState = shippingAddress.state();
            e.shippingPostalCode = shippingAddress.postalCode();
            e.shippingCountry = shippingAddress.country();
        }
        return e;
    }

    Order toDomain() {
        ShippingAddress shippingAddress = shippingLine1 == null ? null : new ShippingAddress(
            shippingRecipientName, shippingLine1, shippingLine2, shippingCity, shippingState,
            shippingPostalCode, shippingCountry);
        return Order.reconstitute(
            id, userId, guestEmail, lines.stream().map(OrderLineEmbeddable::toDomain).toList(), totalCents, status,
            stripeSessionId, stripePaymentIntentId, paymentProcessor, createdAt, paidAt, returnRequestedAt,
            returnedAt, returnReason, shippingAddress);
    }
}
