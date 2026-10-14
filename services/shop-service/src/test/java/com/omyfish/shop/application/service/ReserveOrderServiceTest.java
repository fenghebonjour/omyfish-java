package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.CartEmptyException;
import com.omyfish.shop.domain.exception.InsufficientStockException;
import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveOrderServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks ReserveOrderService service;

    @Test
    void reserve_happyPath_decrementsStockAndCreatesOrderAndClearsCart() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = Cart.createEmpty(CartOwner.ofUser(userId));
        cart.addItem(productId, 2);
        Product product = Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 10, true);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.reserve(CartOwner.ofUser(userId), null);

        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().get(0).quantity()).isEqualTo(2);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void reserve_guestOwner_createsOrderWithGuestEmail() {
        UUID guestToken = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = Cart.createEmpty(CartOwner.ofGuest(guestToken));
        cart.addItem(productId, 1);
        Product product = Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 10, true);

        when(cartRepository.findByGuestToken(guestToken)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.reserve(CartOwner.ofGuest(guestToken), "guest@example.com");

        assertThat(order.getUserId()).isNull();
        assertThat(order.getGuestEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void reserve_emptyCart_throwsCartEmptyException() {
        UUID userId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(Cart.createEmpty(CartOwner.ofUser(userId))));

        assertThatThrownBy(() -> service.reserve(CartOwner.ofUser(userId), null))
            .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void reserve_insufficientStock_throwsInsufficientStockException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = Cart.createEmpty(CartOwner.ofUser(userId));
        cart.addItem(productId, 5);
        Product product = Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 2, true);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.reserve(CartOwner.ofUser(userId), null))
            .isInstanceOf(InsufficientStockException.class);
    }
}
