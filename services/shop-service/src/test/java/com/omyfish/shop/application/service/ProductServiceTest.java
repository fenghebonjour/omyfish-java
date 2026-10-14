package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.model.Product;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repository;
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
}
