package com.omyfish.shop.config;

import com.omyfish.shop.adapter.out.payment.AdyenCheckoutAdapter;
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
import com.omyfish.shop.domain.port.in.ReserveOrderUseCase;
import com.omyfish.shop.domain.port.out.CartRepository;
import com.omyfish.shop.domain.port.out.ImageStoragePort;
import com.omyfish.shop.domain.port.out.OrderRepository;
import com.omyfish.shop.domain.port.out.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    ProductService productService(ProductRepository productRepository, ImageStoragePort imageStoragePort) {
        return new ProductService(productRepository, imageStoragePort);
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
        @Value("${payment.default-processor:stripe}") String defaultProcessor
    ) {
        return new ShopPaymentProcessorRegistry(List.of(stripe, paypal, adyen), defaultProcessor);
    }

    @Bean
    CheckoutOrchestrationService checkoutOrchestrationService(
        ReserveOrderUseCase reserveOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
        OrderRepository orderRepository, ShopPaymentProcessorRegistry processors
    ) {
        return new CheckoutOrchestrationService(reserveOrderUseCase, cancelOrderUseCase, orderRepository, processors);
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
