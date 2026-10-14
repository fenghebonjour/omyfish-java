package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.UUID;

public interface ApproveReturnUseCase {
    Order approveReturn(UUID orderId);
}
