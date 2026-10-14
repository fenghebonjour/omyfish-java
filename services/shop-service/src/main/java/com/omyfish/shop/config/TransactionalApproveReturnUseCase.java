package com.omyfish.shop.config;

import com.omyfish.shop.application.service.OrderService;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.port.in.ApproveReturnUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps the order-approve and the per-line restocks atomic, same as
 * TransactionalCancelOrderUseCase. Unlike checkout, the Stripe refund call here does happen
 * inside the transaction — an admin-only, low-volume action, so holding the DB transaction
 * open for that one HTTP round-trip is an acceptable tradeoff against the extra complexity
 * checkout's split-transaction approach needed for the high-volume path.
 */
class TransactionalApproveReturnUseCase implements ApproveReturnUseCase {

    private final OrderService delegate;

    TransactionalApproveReturnUseCase(OrderService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Order approveReturn(UUID orderId) {
        return delegate.approveReturn(orderId);
    }
}
