package com.omyfish.shop.adapter.out.scheduling;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cancels and restocks any order still PENDING_PAYMENT past a timeout. Unlike Stripe's
 * checkout.session.expired, neither PayPal Orders v2 nor Adyen Sessions fire a webhook for an
 * abandoned one-shot checkout, so Stripe's expiry webhook is no longer the only thing an
 * abandoned checkout needs — this sweep covers all three processors uniformly, and also
 * hardens Stripe's own single point of failure (a dropped expiry webhook today leaves stock
 * stuck too). Reuses CancelOrderUseCase.cancelAndRestock — no new restock logic.
 *
 * Single instance only, same assumption api-gateway's RateLimitFilter and
 * identity-service's ReconciliationJob already make — a second replica would double-process
 * nothing harmful here (cancelAndRestock on an already-cancelled order would just throw via
 * Order's own status guard), but there's no claim/lock step to make this efficient under
 * multiple replicas.
 */
@Component
public class StaleOrderSweepJob {

    private final OrderRepository orderRepository;
    private final CancelOrderUseCase cancelOrder;
    private final Duration timeout;

    public StaleOrderSweepJob(
        OrderRepository orderRepository, CancelOrderUseCase cancelOrder,
        @Value("${shop.stale-order-sweep.timeout-minutes:180}") long timeoutMinutes
    ) {
        this.orderRepository = orderRepository;
        this.cancelOrder = cancelOrder;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    @Scheduled(fixedDelayString = "${shop.stale-order-sweep.interval-minutes:30}", timeUnit = TimeUnit.MINUTES)
    public void sweep() {
        List<Order> stale = orderRepository.findPendingPaymentOlderThan(Instant.now().minus(timeout));
        for (Order order : stale) {
            cancelOrder.cancelAndRestock(order.getId());
        }
    }
}
