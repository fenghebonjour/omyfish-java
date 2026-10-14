package com.omyfish.shop.adapter.out.persistence;

import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.model.OrderStatus;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Backed by the same Postgres image family docker-compose.yml uses, with the real V1-V3
// migrations applied — a round trip through the @ElementCollection-mapped OrderLine/CartItem
// embeddables is the highest-risk mapping in this service, so it's worth covering against a
// real database rather than only mocks.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ShopPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Adapters {
        @Bean
        ProductRepository productRepository(ProductJpaRepository jpa) {
            return new ProductRepositoryAdapter(jpa);
        }

        @Bean
        OrderRepository orderRepository(OrderJpaRepository jpa) {
            return new OrderRepositoryAdapter(jpa);
        }
    }

    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @Test
    void seededProduct_isReadableThroughTheRepositoryAdapter() {
        var product = productRepository
            .findById(UUID.fromString("11111111-1111-1111-1111-111111111001"))
            .orElseThrow();

        assertThat(product.getName()).isEqualTo("ShadowStrike Walking Topwater");
        assertThat(product.getStockQuantity()).isEqualTo(50);
    }

    @Test
    void order_withMultipleLines_roundTripsThroughElementCollection() {
        UUID userId = UUID.randomUUID();
        Order order = Order.createPendingPayment(userId, null, List.of(
            new OrderLine(UUID.randomUUID(), "Jig", 999, 2),
            new OrderLine(UUID.randomUUID(), "Spinnerbait", 1499, 1)));

        orderRepository.save(order);
        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(reloaded.getTotalCents()).isEqualTo(999 * 2 + 1499);
    }
}
