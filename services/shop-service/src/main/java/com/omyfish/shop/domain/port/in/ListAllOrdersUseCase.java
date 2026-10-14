package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.List;

// Admin-only: lists every order regardless of owner, newest first, so an admin doesn't need an
// order id already in hand (e.g. to find which orders are awaiting return approval).
public interface ListAllOrdersUseCase {
    List<Order> listAll();
}
