package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Order;

public interface OrderEventPublisher {
    void publishOrderPlaced(Order order);
}
