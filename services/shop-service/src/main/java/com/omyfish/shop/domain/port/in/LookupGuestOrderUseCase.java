package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;

import java.util.UUID;

// Lets a guest buyer track their order without an account, by proving they know both the
// order id and the email it was placed under. Throws OrderNotFoundException on any mismatch
// (wrong id, wrong email, or an order that actually belongs to a logged-in account) so a
// guess can't be used to probe which order ids exist.
public interface LookupGuestOrderUseCase {
    Order lookup(UUID orderId, String email);
}
