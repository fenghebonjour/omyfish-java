package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.OrderNotFoundException;
import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.ApproveReturnUseCase;
import com.omyfish.shop.domain.port.in.GetOrderUseCase;
import com.omyfish.shop.domain.port.in.ListAllOrdersUseCase;
import com.omyfish.shop.domain.port.in.ListOrdersUseCase;
import com.omyfish.shop.domain.port.in.LookupGuestOrderUseCase;
import com.omyfish.shop.domain.port.in.RequestReturnUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.util.List;
import java.util.UUID;

public class OrderService implements
    ListOrdersUseCase, GetOrderUseCase, RequestReturnUseCase, ApproveReturnUseCase, LookupGuestOrderUseCase,
    ListAllOrdersUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentPort paymentPort;

    public OrderService(
        OrderRepository orderRepository, ProductRepository productRepository, PaymentPort paymentPort
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentPort = paymentPort;
    }

    @Override
    public List<Order> listByUser(UUID userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Order get(UUID orderId, UUID userId) {
        return findOwnedOrder(orderId, userId);
    }

    @Override
    public Order requestReturn(UUID orderId, UUID userId, String reason) {
        Order order = findOwnedOrder(orderId, userId);
        order.requestReturn(reason);
        return orderRepository.save(order);
    }

    @Override
    public Order approveReturn(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.approveReturn();

        paymentPort.refund(order.getStripePaymentIntentId(), order.getTotalCents(), orderId.toString());

        order.getLines().forEach(line -> {
            Product product = productRepository.findById(line.productId())
                .orElseThrow(() -> new ProductNotFoundException(line.productId()));
            product.restock(line.quantity());
            productRepository.save(product);
        });

        return orderRepository.save(order);
    }

    @Override
    public List<Order> listAll() {
        return orderRepository.findAll();
    }

    @Override
    public Order lookup(UUID orderId, String email) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getGuestEmail() == null || !order.getGuestEmail().equalsIgnoreCase(email)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order findOwnedOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }
}
