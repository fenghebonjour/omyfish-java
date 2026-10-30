package com.omyfish.shop.config;

import com.omyfish.shop.adapter.out.payment.AdyenCheckoutAdapter;
import com.omyfish.shop.adapter.out.payment.MockPaymentCheckoutAdapter;
import com.omyfish.shop.adapter.out.payment.PayPalCheckoutAdapter;
import com.omyfish.shop.adapter.out.payment.StripeCheckoutAdapter;
import com.omyfish.shop.application.service.CancelOrderService;
import com.omyfish.shop.application.service.CartService;
import com.omyfish.shop.application.service.CheckoutOrchestrationService;
import com.omyfish.shop.application.service.OrderService;
import com.omyfish.shop.application.service.ProductService;
import com.omyfish.shop.application.service.ReserveOrderService;
import com.omyfish.shop.application.service.ShopPaymentProcessorRegistry;
import com.omyfish.shop.domain.port.in.ApproveReturnUseCase;
import com.omyfish.shop.domain.port.in.CancelOrderUseCase;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.GetProductUseCase;
import com.omyfish.shop.domain.port.in.ListProductsUseCase;
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.in.UploadProductImageUseCase;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.IdempotencyKeyRepository;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import com.omyfish.shop.domain.port.out.OrderEventPublisher;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.PaymentPort;
import com.omyfish.shop.domain.port.out.ProductRepository;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
public class AppConfig {

    // Spring Boot's JacksonAutoConfiguration picks up every com.fasterxml.jackson.databind.Module
    // bean and registers it on the shared ObjectMapper — Afterburner generates bytecode at
    // startup to read/write each DTO's fields directly instead of through reflection, cutting
    // per-request (de)serialization cost at 2,000 QPS.
    @Bean
    AfterburnerModule afterburnerModule() {
        return new AfterburnerModule();
    }

    @Bean
    ProductService productService(ProductRepository productRepository, ImageStoragePort imageStoragePort) {
        return new ProductService(productRepository, imageStoragePort);
    }

    @Bean
    ReadOnlyListProductsUseCase readOnlyListProductsUseCase(ProductService productService) {
        return new ReadOnlyListProductsUseCase(productService);
    }

    @Bean
    @Primary
    ListProductsUseCase cachingListProductsUseCase(
        ReadOnlyListProductsUseCase readOnlyListProductsUseCase, ProductCache productCache
    ) {
        return new CachingListProductsUseCase(readOnlyListProductsUseCase, productCache);
    }

    @Bean
    ReadOnlyGetProductUseCase readOnlyGetProductUseCase(ProductService productService) {
        return new ReadOnlyGetProductUseCase(productService);
    }

    @Bean
    @Primary
    GetProductUseCase cachingGetProductUseCase(
        ReadOnlyGetProductUseCase readOnlyGetProductUseCase, ProductCache productCache
    ) {
        return new CachingGetProductUseCase(readOnlyGetProductUseCase, productCache);
    }

    @Bean
    @Primary
    UploadProductImageUseCase cacheEvictingUploadProductImageUseCase(
        ProductService productService, ProductCache productCache
    ) {
        return new CacheEvictingUploadProductImageUseCase(productService, productCache);
    }

    @Bean
    CartService cartService(CartRepository cartRepository, ProductRepository productRepository) {
        return new CartService(cartRepository, productRepository);
    }

    @Bean
    ReserveOrderService reserveOrderService(
        CartRepository cartRepository, ProductRepository productRepository, OrderRepository orderRepository
    ) {
        return new ReserveOrderService(cartRepository, productRepository, orderRepository);
    }

    @Bean
    @Primary
    ReserveOrderUseCase transactionalReserveOrderUseCase(ReserveOrderService reserveOrderService) {
        return new TransactionalReserveOrderUseCase(reserveOrderService);
    }

    @Bean
    CancelOrderService cancelOrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        return new CancelOrderService(orderRepository, productRepository);
    }

    @Bean
    @Primary
    CancelOrderUseCase transactionalCancelOrderUseCase(CancelOrderService cancelOrderService) {
        return new TransactionalCancelOrderUseCase(cancelOrderService);
    }

    @Bean
    ShopPaymentProcessorRegistry shopPaymentProcessorRegistry(
        StripeCheckoutAdapter stripe, PayPalCheckoutAdapter paypal, AdyenCheckoutAdapter adyen,
        Optional<MockPaymentCheckoutAdapter> mock,
        @Value("${payment.default-processor:stripe}") String defaultProcessor
    ) {
        List<PaymentPort> processors = new ArrayList<>(List.of(stripe, paypal, adyen));
        mock.ifPresent(processors::add);
        return new ShopPaymentProcessorRegistry(processors, defaultProcessor);
    }

    @Bean
    CheckoutOrchestrationService checkoutOrchestrationService(
        ReserveOrderUseCase reserveOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
        OrderRepository orderRepository, ShopPaymentProcessorRegistry processors,
        IdempotencyKeyRepository idempotencyKeyRepository, OrderEventPublisher orderEventPublisher
    ) {
        return new CheckoutOrchestrationService(
            reserveOrderUseCase, cancelOrderUseCase, orderRepository, processors, idempotencyKeyRepository,
            orderEventPublisher);
    }

    @Bean
    @Primary
    CheckoutUseCase meteredCheckoutUseCase(
        CheckoutOrchestrationService checkoutOrchestrationService, MeterRegistry meterRegistry
    ) {
        return new MeteredCheckoutUseCase(checkoutOrchestrationService, meterRegistry);
    }

    @Bean
    OrderService orderService(
        OrderRepository orderRepository, ProductRepository productRepository, ShopPaymentProcessorRegistry processors
    ) {
        return new OrderService(orderRepository, productRepository, processors);
    }

    @Bean
    @Primary
    ApproveReturnUseCase transactionalApproveReturnUseCase(OrderService orderService) {
        return new TransactionalApproveReturnUseCase(orderService);
    }
}
