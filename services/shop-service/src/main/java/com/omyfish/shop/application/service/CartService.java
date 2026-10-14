package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Cart;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.AddToCartUseCase;
import com.omyfish.shop.domain.port.in.MergeGuestCartUseCase;
import com.omyfish.shop.domain.port.in.UpdateCartItemUseCase;
import com.omyfish.shop.domain.port.in.ViewCartUseCase;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.util.Optional;
import java.util.UUID;

public class CartService implements ViewCartUseCase, AddToCartUseCase, UpdateCartItemUseCase, MergeGuestCartUseCase {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Cart getOrCreateCart(CartOwner owner) {
        return findByOwner(owner)
            .orElseGet(() -> cartRepository.save(Cart.createEmpty(owner)));
    }

    private Optional<Cart> findByOwner(CartOwner owner) {
        return owner.isGuest()
            ? cartRepository.findByGuestToken(owner.guestToken())
            : cartRepository.findByUserId(owner.userId());
    }

    @Override
    public Cart addItem(CartOwner owner, UUID productId, int quantity) {
        requireProductExists(productId);
        Cart cart = getOrCreateCart(owner);
        cart.addItem(productId, quantity);
        return cartRepository.save(cart);
    }

    @Override
    public Cart updateQuantity(CartOwner owner, UUID productId, int quantity) {
        if (quantity > 0) {
            requireProductExists(productId);
        }
        Cart cart = getOrCreateCart(owner);
        cart.setQuantity(productId, quantity);
        return cartRepository.save(cart);
    }

    @Override
    public Cart merge(UUID userId, UUID guestToken) {
        Cart userCart = getOrCreateCart(CartOwner.ofUser(userId));
        var guestCart = cartRepository.findByGuestToken(guestToken);
        if (guestCart.isPresent()) {
            userCart.mergeFrom(guestCart.get());
            userCart = cartRepository.save(userCart);
            cartRepository.deleteByGuestToken(guestToken);
        }
        return userCart;
    }

    private void requireProductExists(UUID productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
