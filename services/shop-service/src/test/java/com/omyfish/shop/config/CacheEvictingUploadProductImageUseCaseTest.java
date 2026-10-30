package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.UploadProductImageUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheEvictingUploadProductImageUseCaseTest {

    @Mock UploadProductImageUseCase delegate;
    @Mock ProductCache cache;

    @Test
    void uploadImage_delegatesThenEvictsProductCache() {
        CacheEvictingUploadProductImageUseCase useCase = new CacheEvictingUploadProductImageUseCase(delegate, cache);
        UUID id = UUID.randomUUID();
        Product updated = Product.reconstitute(id, "Jig", "desc", 999, "img.jpg", 10, true);
        InputStream data = new ByteArrayInputStream(new byte[0]);
        when(delegate.uploadImage(eq(id), any(), eq(0L), eq("image/png"), eq("photo.png"))).thenReturn(updated);

        Product result = useCase.uploadImage(id, data, 0L, "image/png", "photo.png");

        assertThat(result).isSameAs(updated);
        verify(cache).evictProduct(id);
    }
}
