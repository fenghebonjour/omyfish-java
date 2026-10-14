package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks CartService service;

    @Test
    void addItem_productExists_addsToNewCartAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofUser(userId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(
            Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 10, true)));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = service.addItem(owner, productId, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void addItem_guestOwner_addsToNewGuestCartAndSaves() {
        UUID guestToken = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CartOwner owner = CartOwner.ofGuest(guestToken);
        when(productRepository.findById(productId)).thenReturn(Optional.of(
            Product.reconstitute(productId, "Jig", "desc", 999, "img.jpg", 10, true)));
        when(cartRepository.findByGuestToken(guestToken)).thenReturn(Optional.empty());
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = service.addItem(owner, productId, 1);

        assertThat(result.isGuest()).isTrue();
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void addItem_productMissing_throwsProductNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(CartOwner.ofUser(userId), productId, 1))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateQuantity_zero_removesItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart existing = Cart.createEmpty(CartOwner.ofUser(userId));
        existing.addItem(productId, 3);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        when(cartRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = service.updateQuantity(CartOwner.ofUser(userId), productId, 0);

        assertThat(result.getItems()).isEmpty();
        verify(cartRepository).save(any());
    }

    @Test
    void merge_guestCartExists_foldsItemsIntoUserCartAndDeletesGuestCart() {
        UUID userId = UUID.randomUUID();
        UUID guestToken = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Cart userCart = Cart.createEmpty(CartOwner.ofUser(userId));
        Cart guestCart = Cart.createEmpty(CartOwner.ofGuest(guestToken));
        guestCart.addItem(productId, 2);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(userCart));
        when(cartRepository.findByGuestToken(guestToken)).thenReturn(Optional.of(guestCart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = service.merge(userId, guestToken);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).quantity()).isEqualTo(2);
        verify(cartRepository).deleteByGuestToken(guestToken);
    }

    @Test
    void merge_noGuestCart_returnsUserCartUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID guestToken = UUID.randomUUID();
        Cart userCart = Cart.createEmpty(CartOwner.ofUser(userId));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(userCart));
        when(cartRepository.findByGuestToken(guestToken)).thenReturn(Optional.empty());

        Cart result = service.merge(userId, guestToken);

        assertThat(result.getItems()).isEmpty();
        verify(cartRepository, org.mockito.Mockito.never()).deleteByGuestToken(any());
    }
}
