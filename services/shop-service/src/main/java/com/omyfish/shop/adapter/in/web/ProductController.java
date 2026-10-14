package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.ProductResponse;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Public catalog browsing — whitelisted in the gateway AuthFilter, so no account needed.
@RestController
@RequestMapping("/api/v1/shop/products")
public class ProductController {

    private final ListProductsUseCase listProductsUseCase;
    private final GetProductUseCase getProductUseCase;

    public ProductController(ListProductsUseCase listProductsUseCase, GetProductUseCase getProductUseCase) {
        this.listProductsUseCase = listProductsUseCase;
        this.getProductUseCase = getProductUseCase;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> list() {
        return ResponseEntity.ok(listProductsUseCase.listActive().stream().map(ProductResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ProductResponse.from(getProductUseCase.get(id)));
    }
}
