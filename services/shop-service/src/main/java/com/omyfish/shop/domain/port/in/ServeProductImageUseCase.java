package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.valueobject.StoredImage;

import java.util.UUID;

// Public: streams back a product's uploaded photo. Throws ProductNotFoundException if the
// product doesn't exist, or if it never got a real upload (still using its seeded placeholder).
public interface ServeProductImageUseCase {
    StoredImage serve(UUID productId);
}
