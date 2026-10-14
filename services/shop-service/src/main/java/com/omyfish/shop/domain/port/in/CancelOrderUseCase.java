package com.omyfish.shop.domain.port.in;

import java.util.UUID;

// Cancels a PENDING_PAYMENT order and restocks its lines, atomically. Used both as the
// compensating action when creating the Stripe checkout session fails, and when Stripe
// reports the checkout session itself expired unpaid.
public interface CancelOrderUseCase {
    void cancelAndRestock(UUID orderId);
}
