package com.omyfish.shop.adapter.in.web.dto;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id, OrderStatus status, long totalCents, List<Line> lines,
    Instant createdAt, Instant paidAt, Instant returnRequestedAt, Instant returnedAt, String returnReason,
    ShippingAddressResponse shippingAddress
) {
    public record Line(UUID productId, String productName, long unitPriceCents, int quantity) {}

    public record ShippingAddressResponse(
        String recipientName, String line1, String line2, String city, String state,
        String postalCode, String country
    ) {
        static ShippingAddressResponse from(ShippingAddress a) {
            return new ShippingAddressResponse(
                a.recipientName(), a.line1(), a.line2(), a.city(), a.state(), a.postalCode(), a.country());
        }
    }

    public static OrderResponse from(Order o) {
        List<Line> lines = o.getLines().stream()
            .map(l -> new Line(l.productId(), l.productName(), l.unitPriceCents(), l.quantity()))
            .toList();
        ShippingAddressResponse shippingAddress = o.getShippingAddress() == null
            ? null : ShippingAddressResponse.from(o.getShippingAddress());
        return new OrderResponse(
            o.getId(), o.getStatus(), o.getTotalCents(), lines,
            o.getCreatedAt(), o.getPaidAt(), o.getReturnRequestedAt(), o.getReturnedAt(), o.getReturnReason(),
            shippingAddress);
    }
}
