package com.omyfish.shop.adapter.out.scheduling;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleOrderSweepJobTest {

    @Mock OrderRepository orderRepository;
    @Mock CancelOrderUseCase cancelOrder;

    @Test
    void sweep_staleOrders_areCancelledAndRestocked() {
        Order stale1 = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        Order stale2 = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Lure", 1500, 1)));
        when(orderRepository.findPendingPaymentOlderThan(any(Instant.class))).thenReturn(List.of(stale1, stale2));

        StaleOrderSweepJob job = new StaleOrderSweepJob(orderRepository, cancelOrder, 180L);
        job.sweep();

        verify(cancelOrder).cancelAndRestock(stale1.getId());
        verify(cancelOrder).cancelAndRestock(stale2.getId());
    }

    @Test
    void sweep_nothingStale_doesNothing() {
        when(orderRepository.findPendingPaymentOlderThan(any(Instant.class))).thenReturn(List.of());

        StaleOrderSweepJob job = new StaleOrderSweepJob(orderRepository, cancelOrder, 180L);
        job.sweep();

        verify(cancelOrder, org.mockito.Mockito.never()).cancelAndRestock(any());
    }
}
