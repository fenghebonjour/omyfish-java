package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.CartItem;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReserveOrderService implements ReserveOrderUseCase {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public ReserveOrderService(
        CartRepository cartRepository, ProductRepository productRepository, OrderRepository orderRepository
    ) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public Order reserve(CartOwner owner, String guestEmail) {
        Cart cart = findByOwner(owner)
            .orElseGet(() -> Cart.createEmpty(owner));
        cart.requireNotEmpty();

        List<OrderLine> lines = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new ProductNotFoundException(item.productId()));
            product.decreaseStock(item.quantity());
            productRepository.save(product);
            lines.add(new OrderLine(product.getId(), product.getName(), product.getPriceCents(), item.quantity()));
        }

        Order order = orderRepository.save(Order.createPendingPayment(owner.userId(), guestEmail, lines));

        cart.clear();
        cartRepository.save(cart);

        return order;
    }

    private Optional<Cart> findByOwner(CartOwner owner) {
        return owner.isGuest()
            ? cartRepository.findByGuestToken(owner.guestToken())
            : cartRepository.findByUserId(owner.userId());
    }
}
