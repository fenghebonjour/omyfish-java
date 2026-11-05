package com.omyfish.shop.config;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingGetProductUseCaseTest {

    @Mock GetProductUseCase delegate;
    @Mock ProductCache cache;

    @Test
    void get_knownMissing_throwsWithoutCallingDelegate() {
        UUID id = UUID.randomUUID();
        CachingGetProductUseCase useCase = new CachingGetProductUseCase(delegate, cache);
        when(cache.isKnownMissing(id)).thenReturn(true);

        assertThatThrownBy(() -> useCase.get(id)).isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(delegate);
    }

    @Test
    void get_cacheHit_returnsCachedWithoutCallingDelegate() {
        UUID id = UUID.randomUUID();
        CachingGetProductUseCase useCase = new CachingGetProductUseCase(delegate, cache);
        Product cached = Product.reconstitute(id, "Jig", "desc", 999, "img.jpg", 10, true);
        when(cache.isKnownMissing(id)).thenReturn(false);
        when(cache.getProduct(id)).thenReturn(Optional.of(cached));

        Product result = useCase.get(id);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(delegate);
    }

    @Test
    void get_cacheMiss_callsDelegateAndPopulatesCache() {
        UUID id = UUID.randomUUID();
        CachingGetProductUseCase useCase = new CachingGetProductUseCase(delegate, cache);
        Product fresh = Product.reconstitute(id, "Jig", "desc", 999, "img.jpg", 10, true);
        when(cache.isKnownMissing(id)).thenReturn(false);
        when(cache.getProduct(id)).thenReturn(Optional.empty());
        when(delegate.get(id)).thenReturn(fresh);

        Product result = useCase.get(id);

        assertThat(result).isSameAs(fresh);
        verify(cache).putProduct(fresh);
    }

    @Test
    void get_delegateThrowsNotFound_marksMissingAndRethrows() {
        UUID id = UUID.randomUUID();
        CachingGetProductUseCase useCase = new CachingGetProductUseCase(delegate, cache);
        when(cache.isKnownMissing(id)).thenReturn(false);
        when(cache.getProduct(id)).thenReturn(Optional.empty());
        when(delegate.get(id)).thenThrow(new ProductNotFoundException(id));

        assertThatThrownBy(() -> useCase.get(id)).isInstanceOf(ProductNotFoundException.class);

        verify(cache).markMissing(id);
        verify(cache, never()).putProduct(any());
    }
}
