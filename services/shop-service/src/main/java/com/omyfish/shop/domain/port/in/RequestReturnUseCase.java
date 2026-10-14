package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.UUID;

public interface RequestReturnUseCase {
    Order requestReturn(UUID orderId, UUID userId, String reason);
}
