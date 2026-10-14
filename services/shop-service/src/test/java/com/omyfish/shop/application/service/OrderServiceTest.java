package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.OrderNotFoundException;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock PaymentPort paymentPort;
    @InjectMocks OrderService service;

    @Test
    void get_orderBelongsToDifferentUser_throwsOrderNotFoundException() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Order order = Order.createPendingPayment(owner, null, List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.get(order.getId(), stranger))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void requestReturn_ownerWithinWindow_setsReturnRequested() {
        UUID userId = UUID.randomUUID();
        Order order = Order.createPendingPayment(userId, null, List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        order.markPaid("pi_1", null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.requestReturn(order.getId(), userId, "wrong size");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(result.getReturnReason()).isEqualTo("wrong size");
    }

    @Test
    void approveReturn_refundsAndRestocksAndMarksReturned() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Order order = Order.createPendingPayment(userId, null, List.of(new OrderLine(productId, "Jig", 999, 2)));
        order.markPaid("pi_1", null);
        order.requestReturn("no longer needed");
        Product product = Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 5, true);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentPort.refund(anyString(), anyLong(), anyString())).thenReturn(Optional.empty());

        Order result = service.approveReturn(order.getId());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(paymentPort).refund("pi_1", order.getTotalCents(), order.getId().toString());
    }

    @Test
    void lookup_matchingGuestEmail_returnsOrder() {
        Order order = Order.createPendingPayment(null, "guest@example.com",
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        Order result = service.lookup(order.getId(), "GUEST@example.com");

        assertThat(result.getId()).isEqualTo(order.getId());
    }

    @Test
    void lookup_wrongEmail_throwsOrderNotFoundException() {
        Order order = Order.createPendingPayment(null, "guest@example.com",
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.lookup(order.getId(), "someone-else@example.com"))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void lookup_accountOrder_throwsOrderNotFoundException() {
        Order order = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.lookup(order.getId(), "anything@example.com"))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listAll_delegatesToRepositoryRegardlessOfOwner() {
        Order accountOrder = Order.createPendingPayment(UUID.randomUUID(), null,
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        Order guestOrder = Order.createPendingPayment(null, "guest@example.com",
            List.of(new OrderLine(UUID.randomUUID(), "Jig", 999, 1)));
        when(orderRepository.findAll()).thenReturn(List.of(accountOrder, guestOrder));

        List<Order> result = service.listAll();

        assertThat(result).containsExactly(accountOrder, guestOrder);
    }
}
