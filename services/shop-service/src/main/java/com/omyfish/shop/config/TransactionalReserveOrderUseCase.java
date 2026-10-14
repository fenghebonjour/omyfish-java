package com.omyfish.shop.config;

import com.omyfish.shop.application.service.ReserveOrderService;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps {@code ReserveOrderService.reserve()} in one DB transaction so the stock decrements,
 * the order insert, and the cart clear either all commit or all roll back together — the same
 * atomicity concern observation-service's TransactionalCreateObservationUseCase documents for
 * its own insert pair. {@code application/} stays free of Spring annotations, so the
 * transactional boundary lives here instead.
 */
class TransactionalReserveOrderUseCase implements ReserveOrderUseCase {

    private final ReserveOrderService delegate;

    TransactionalReserveOrderUseCase(ReserveOrderService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Order reserve(CartOwner owner, String guestEmail) {
        return delegate.reserve(owner, guestEmail);
    }
}
