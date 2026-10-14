package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.ProductResponse;
import com.omyfish.shop.domain.port.in.UploadProductImageUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

// Admin-only, and deliberately outside /api/v1/shop/products — that prefix is public for
// catalog browsing, but this write path must still require a valid JWT at the gateway (same
// double-layered protection as OrderController's return-approval endpoint).
@RestController
@RequestMapping("/api/v1/shop/admin/products")
public class AdminProductController {

    private final UploadProductImageUseCase uploadProductImageUseCase;

    public AdminProductController(UploadProductImageUseCase uploadProductImageUseCase) {
        this.uploadProductImageUseCase = uploadProductImageUseCase;
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> uploadImage(
        @PathVariable("id") UUID id, @RequestHeader("X-User-Role") String userRoleHeader,
        @RequestParam("image") MultipartFile image
    ) throws IOException {
        if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        var product = uploadProductImageUseCase.uploadImage(
            id, image.getInputStream(), image.getSize(), image.getContentType(), image.getOriginalFilename());
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}
