package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.valueobject.CartOwner;

// Decrements stock, creates the PENDING_PAYMENT order, and clears the cart, all atomically —
// a sub-step of CheckoutUseCase, split out so it can be wrapped in its own DB transaction
// while the Stripe call that follows it happens outside that transaction (see
// CheckoutOrchestrationService).
public interface ReserveOrderUseCase {
    // guestEmail is required when owner.isGuest(), and ignored otherwise.
    Order reserve(CartOwner owner, String guestEmail);
}
