package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.StoredImage;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import com.omyfish.shop.domain.port.in.ServeProductImageUseCase;
import com.omyfish.shop.domain.port.in.UploadProductImageUseCase;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import com.omyfish.shop.domain.port.out.ProductRepository;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class ProductService implements
    ListProductsUseCase, GetProductUseCase, UploadProductImageUseCase, ServeProductImageUseCase {

    private final ProductRepository repository;
    private final ImageStoragePort imageStoragePort;

    public ProductService(ProductRepository repository, ImageStoragePort imageStoragePort) {
        this.repository = repository;
        this.imageStoragePort = imageStoragePort;
    }

    @Override
    public List<Product> listActive() {
        return repository.findAllActive();
    }

    @Override
    public Product get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public Product uploadImage(UUID productId, InputStream data, long size, String contentType, String originalFilename) {
        Product product = get(productId);
        String key = imageStoragePort.store(data, size, contentType, originalFilename);
        product.setImage("/api/v1/shop/products/" + productId + "/image", key);
        return repository.save(product);
    }

    @Override
    public StoredImage serve(UUID productId) {
        Product product = get(productId);
        if (product.getImageStorageKey() == null) {
            throw new ProductNotFoundException(productId);
        }
        return imageStoragePort.load(product.getImageStorageKey());
    }
}
