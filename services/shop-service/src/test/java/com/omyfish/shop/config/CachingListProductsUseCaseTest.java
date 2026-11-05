package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingListProductsUseCaseTest {

    @Mock ListProductsUseCase delegate;
    @Mock ProductCache cache;

    @Test
    void listActive_cacheHit_returnsCachedWithoutCallingDelegate() {
        CachingListProductsUseCase useCase = new CachingListProductsUseCase(delegate, cache);
        List<Product> cached = List.of(Product.reconstitute(
            UUID.randomUUID(), "Jig", "desc", 999, "img.jpg", 10, true));
        when(cache.getList()).thenReturn(Optional.of(cached));

        List<Product> result = useCase.listActive();

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(delegate);
    }

    @Test
    void listActive_cacheMiss_callsDelegateAndPopulatesCache() {
        CachingListProductsUseCase useCase = new CachingListProductsUseCase(delegate, cache);
        List<Product> fresh = List.of(Product.reconstitute(
            UUID.randomUUID(), "Jig", "desc", 999, "img.jpg", 10, true));
        when(cache.getList()).thenReturn(Optional.empty());
        when(delegate.listActive()).thenReturn(fresh);

        List<Product> result = useCase.listActive();

        assertThat(result).isSameAs(fresh);
        verify(cache).putList(fresh);
    }
}
