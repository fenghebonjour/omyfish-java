package com.omyfish.shop.domain.port.in;

import com.omyfish.shop.domain.model.Product;

import java.io.InputStream;
import java.util.UUID;

// Admin-only: replaces a product's photo.
public interface UploadProductImageUseCase {
    Product uploadImage(UUID productId, InputStream data, long size, String contentType, String originalFilename);
}
