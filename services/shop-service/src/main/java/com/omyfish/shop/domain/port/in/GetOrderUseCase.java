package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.UUID;

public interface GetOrderUseCase {
    // Throws OrderNotFoundException if no such order exists or it does not belong to userId.
    Order get(UUID orderId, UUID userId);
}
