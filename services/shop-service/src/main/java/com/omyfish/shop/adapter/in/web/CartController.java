package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.AddCartItemRequest;
import com.omyfish.shop.adapter.in.web.dto.CartResponse;
import com.omyfish.shop.adapter.in.web.dto.MergeCartRequest;
import com.omyfish.shop.adapter.in.web.dto.UpdateCartItemRequest;
import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.AddToCartUseCase;
import com.omyfish.shop.domain.port.in.MergeGuestCartUseCase;
import com.omyfish.shop.domain.port.in.UpdateCartItemUseCase;
import com.omyfish.shop.domain.port.in.ViewCartUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shop/cart")
public class CartController {

    private final ViewCartUseCase viewCartUseCase;
    private final AddToCartUseCase addToCartUseCase;
    private final UpdateCartItemUseCase updateCartItemUseCase;
    private final MergeGuestCartUseCase mergeGuestCartUseCase;

    public CartController(
        ViewCartUseCase viewCartUseCase, AddToCartUseCase addToCartUseCase,
        UpdateCartItemUseCase updateCartItemUseCase, MergeGuestCartUseCase mergeGuestCartUseCase
    ) {
        this.viewCartUseCase = viewCartUseCase;
        this.addToCartUseCase = addToCartUseCase;
        this.updateCartItemUseCase = updateCartItemUseCase;
        this.mergeGuestCartUseCase = mergeGuestCartUseCase;
    }

    @GetMapping
    public ResponseEntity<CartResponse> view(
        @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
        @RequestHeader(value = "X-Cart-Token", required = false) String cartTokenHeader
    ) {
        CartOwner owner = CartOwnerResolver.resolve(userIdHeader, cartTokenHeader);
        return ResponseEntity.ok(CartResponse.from(viewCartUseCase.getOrCreateCart(owner)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
        @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
        @RequestHeader(value = "X-Cart-Token", required = false) String cartTokenHeader,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartOwner owner = CartOwnerResolver.resolve(userIdHeader, cartTokenHeader);
        var cart = addToCartUseCase.addItem(owner, request.productId(), request.quantity());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
        @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
        @RequestHeader(value = "X-Cart-Token", required = false) String cartTokenHeader,
        @PathVariable UUID productId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartOwner owner = CartOwnerResolver.resolve(userIdHeader, cartTokenHeader);
        var cart = updateCartItemUseCase.updateQuantity(owner, productId, request.quantity());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    // Authenticated only — folds the guest cart named by request.guestToken() into the caller's
    // account cart. Called by the frontend right after login/register when a guest cart token
    // is still in storage.
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> merge(
        @RequestHeader("X-User-Id") String userIdHeader, @RequestBody MergeCartRequest request
    ) {
        var cart = mergeGuestCartUseCase.merge(UUID.fromString(userIdHeader), request.guestToken());
        return ResponseEntity.ok(CartResponse.from(cart));
    }
}
