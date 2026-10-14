package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.OrderNotFoundException;
import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.util.UUID;

public class CancelOrderService implements CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public CancelOrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void cancelAndRestock(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markCancelled();

        order.getLines().forEach(line -> {
            Product product = productRepository.findById(line.productId())
                .orElseThrow(() -> new ProductNotFoundException(line.productId()));
            product.restock(line.quantity());
            productRepository.save(product);
        });

        orderRepository.save(order);
    }
}
