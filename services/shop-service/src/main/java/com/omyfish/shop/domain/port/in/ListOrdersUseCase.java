package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.List;
import java.util.UUID;

public interface ListOrdersUseCase {
    List<Order> listByUser(UUID userId);
}
