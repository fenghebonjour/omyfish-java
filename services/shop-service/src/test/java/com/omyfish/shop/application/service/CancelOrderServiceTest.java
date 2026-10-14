package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks CancelOrderService service;

    @Test
    void cancelAndRestock_restocksEachLineAndCancelsOrder() {
        UUID productId = UUID.randomUUID();
        Order order = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(productId, "Jig", 999, 3)));
        Product product = Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 7, true);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(savedOrder.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelAndRestock(order.getId());

        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(savedOrder.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
