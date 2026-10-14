package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.model.valueobject.StoredImage;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repository;
    @Mock ImageStoragePort imageStoragePort;
    @InjectMocks ProductService service;

    @Test
    void listActive_delegatesToRepository() {
        Product p = Product.reconstitute(UUID.randomUUID(), "Spinnerbait", "desc", 1499, "img.jpg", 10, true);
        when(repository.findAllActive()).thenReturn(List.of(p));

        List<Product> result = service.listActive();

        assertThat(result).containsExactly(p);
    }

    @Test
    void get_notFound_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void uploadImage_storesAndPointsImageUrlAtOwnServeEndpoint() {
        UUID id = UUID.randomUUID();
        Product p = Product.reconstitute(id, "Spinnerbait", "desc", 1499, "placeholder.svg", 10, true);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        when(imageStoragePort.store(any(), eq(5L), eq("image/png"), eq("photo.png"))).thenReturn("products/abc.png");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        InputStream data = new ByteArrayInputStream(new byte[5]);

        Product result = service.uploadImage(id, data, 5L, "image/png", "photo.png");

        assertThat(result.getImageUrl()).isEqualTo("/api/v1/shop/products/" + id + "/image");
        assertThat(result.getImageStorageKey()).isEqualTo("products/abc.png");
    }

    @Test
    void serve_productWithUploadedImage_loadsFromStorage() {
        UUID id = UUID.randomUUID();
        Product p = Product.reconstitute(
            id, "Spinnerbait", "desc", 1499, "/api/v1/shop/products/" + id + "/image", "products/abc.png", 10, true);
        StoredImage stored = new StoredImage(new ByteArrayInputStream(new byte[0]), "image/png");
        when(repository.findById(id)).thenReturn(Optional.of(p));
        when(imageStoragePort.load("products/abc.png")).thenReturn(stored);

        StoredImage result = service.serve(id);

        assertThat(result).isSameAs(stored);
    }

    @Test
    void serve_productWithoutUploadedImage_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        Product p = Product.reconstitute(id, "Spinnerbait", "desc", 1499, "placeholder.svg", 10, true);
        when(repository.findById(id)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.serve(id)).isInstanceOf(ProductNotFoundException.class);
    }
}
