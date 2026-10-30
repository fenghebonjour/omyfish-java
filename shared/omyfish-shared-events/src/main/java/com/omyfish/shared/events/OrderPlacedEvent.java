package com.omyfish.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
    String eventId,
    Instant occurredOn,
    UUID orderId,
    UUID userId,
    String guestEmail,
    long totalCents,
    List<Line> lines
) {
    public OrderPlacedEvent(UUID orderId, UUID userId, String guestEmail, long totalCents, List<Line> lines) {
        this(UUID.randomUUID().toString(), Instant.now(), orderId, userId, guestEmail, totalCents, lines);
    }

    public record Line(UUID productId, String productName, int quantity, long unitPriceCents) {}

    // Partitioning by the first line's productId keeps every event for the same hot SKU ordered
    // through one consumer — the common flash-sale case is a single-product cart; a multi-line
    // order's later lines don't get that same-partition guarantee, which is an accepted
    // tradeoff at this scale rather than a per-line event stream.
    public UUID partitionKey() {
        return lines.get(0).productId();
    }

    public static final String TOPIC = "omyfish.shop.orders";
}
