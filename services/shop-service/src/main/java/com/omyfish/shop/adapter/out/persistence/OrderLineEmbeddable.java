package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.valueobject.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
class OrderLineEmbeddable {

    @Column(name = "product_id")
    private UUID productId;
    private String productName;
    private long unitPriceCents;
    private int quantity;

    protected OrderLineEmbeddable() {}

    static OrderLineEmbeddable from(OrderLine line) {
        OrderLineEmbeddable e = new OrderLineEmbeddable();
        e.productId = line.productId();
        e.productName = line.productName();
        e.unitPriceCents = line.unitPriceCents();
        e.quantity = line.quantity();
        return e;
    }

    OrderLine toDomain() {
        return new OrderLine(productId, productName, unitPriceCents, quantity);
    }
}
