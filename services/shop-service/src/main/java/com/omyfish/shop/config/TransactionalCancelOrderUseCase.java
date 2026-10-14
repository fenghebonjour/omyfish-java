package com.omyfish.shop.config;

import com.omyfish.shop.application.service.CancelOrderService;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Keeps the order-cancel and the per-line restocks atomic. See TransactionalReserveOrderUseCase. */
class TransactionalCancelOrderUseCase implements CancelOrderUseCase {

    private final CancelOrderService delegate;

    TransactionalCancelOrderUseCase(CancelOrderService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void cancelAndRestock(UUID orderId) {
        delegate.cancelAndRestock(orderId);
    }
}
