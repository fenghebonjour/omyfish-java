package com.omyfish.shop.adapter.in.web.dto;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id, OrderStatus status, long totalCents, List<Line> lines,
    Instant createdAt, Instant paidAt, Instant returnRequestedAt, Instant returnedAt, String returnReason
) {
    public record Line(UUID productId, String productName, long unitPriceCents, int quantity) {}

    public static OrderResponse from(Order o) {
        List<Line> lines = o.getLines().stream()
            .map(l -> new Line(l.productId(), l.productName(), l.unitPriceCents(), l.quantity()))
            .toList();
        return new OrderResponse(
            o.getId(), o.getStatus(), o.getTotalCents(), lines,
            o.getCreatedAt(), o.getPaidAt(), o.getReturnRequestedAt(), o.getReturnedAt(), o.getReturnReason());
    }
}
