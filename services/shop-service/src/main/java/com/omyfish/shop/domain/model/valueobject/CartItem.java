package com.omyfish.shop.domain.model.valueobject;

import java.util.UUID;

public record CartItem(UUID productId, int quantity) {
}
