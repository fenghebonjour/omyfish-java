package com.omyfish.shop.domain.model.valueobject;

import java.util.UUID;

// unitPriceCents and productName are snapshotted at checkout time so a later catalog price or
// name change never alters a past order's recorded total.
public record OrderLine(UUID productId, String productName, long unitPriceCents, int quantity) {
    public long subtotalCents() {
        return unitPriceCents * quantity;
    }
}
