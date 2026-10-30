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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void seededProduct_isReadableThroughTheRepositoryAdapter() {
        var product = productRepository
            .findById(UUID.fromString("11111111-1111-1111-1111-111111111001"))
            .orElseThrow();

        assertThat(product.getName()).isEqualTo("ShadowStrike Walking Topwater");
        assertThat(product.getStockQuantity()).isEqualTo(50);
    }

    // Proves the fix for the real overselling bug this flash-sale hardening work found: the old
    // load->decreaseStock()->save path let two concurrent checkouts both read the same
    // stockQuantity and both decrement it. Stock is first drained down to exactly 5 units, then
    // 20 threads race to take 1 unit each — against a real Postgres, not a mock, since only a
    // real database proves the atomic conditional UPDATE actually serializes the decrements
    // instead of losing some of them.
    // @DataJpaTest wraps each test in one rolled-back transaction by default. That's wrong here:
    // it'd hold the row lock from the seeding decrement open for the whole method, serializing
    // (and starving) the 20 callers' own transactions instead of letting them race for real. So
    // this test opts out (NOT_SUPPORTED) and instead commits each call in its own REQUIRES_NEW
    // transaction via TransactionTemplate — including for the 20 pool threads, which never had
    // the main thread's ambient test transaction to begin with.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void tryDecreaseStock_underConcurrentCallers_neverOversells() throws InterruptedException {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111002");
        int available = newTx.execute(status ->
            productRepository.findById(productId).orElseThrow().getStockQuantity());
        while (available > 5) {
            int decrement = available - 5;
            newTx.executeWithoutResult(status -> productRepository.tryDecreaseStock(productId, decrement));
            available = newTx.execute(status ->
                productRepository.findById(productId).orElseThrow().getStockQuantity());
        }
        assertThat(available).isEqualTo(5);

        int callers = 20;
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    boolean decreased = Boolean.TRUE.equals(
                        newTx.execute(status -> productRepository.tryDecreaseStock(productId, 1)));
                    if (decreased) {
                        successes.incrementAndGet();
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        int remaining = newTx.execute(status ->
            productRepository.findById(productId).orElseThrow().getStockQuantity());
        assertThat(successes.get()).isEqualTo(5);
        assertThat(remaining).isZero();
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
