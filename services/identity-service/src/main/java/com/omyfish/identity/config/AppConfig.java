package com.omyfish.identity.config;

import com.omyfish.identity.adapter.out.payment.AdyenPaymentAdapter;
import com.omyfish.identity.adapter.out.payment.PayPalPaymentAdapter;
import com.omyfish.identity.adapter.out.payment.StripePaymentAdapter;
import com.omyfish.identity.adapter.out.persistence.ApiKeyRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.IdempotencyKeyRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.ProcessedWebhookEventRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.SubscriptionRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.UserRepositoryAdapter;
import com.omyfish.identity.adapter.out.security.JwtTokenAdapter;
import com.omyfish.identity.application.service.AuthService;
import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.application.service.PaymentProcessorRegistry;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class AppConfig {

    private AuthService authService(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return new AuthService(
            userRepository, apiKeyRepository, subscriptionRepository, passwordEncoder, tokenPort);
    }

    @Bean
    public RegisterUseCase registerUseCase(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return authService(userRepository, apiKeyRepository, subscriptionRepository,
            passwordEncoder, tokenPort)::register;
    }

    @Bean
    public LoginUseCase loginUseCase(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return authService(userRepository, apiKeyRepository, subscriptionRepository,
            passwordEncoder, tokenPort)::login;
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return authService(userRepository, apiKeyRepository, subscriptionRepository,
            passwordEncoder, tokenPort)::refresh;
    }

    @Bean
    public GetCurrentUserUseCase getCurrentUserUseCase(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return authService(userRepository, apiKeyRepository, subscriptionRepository,
            passwordEncoder, tokenPort)::me;
    }

    @Bean
    public CreateApiKeyUseCase createApiKeyUseCase(
        UserRepositoryAdapter userRepository,
        ApiKeyRepositoryAdapter apiKeyRepository,
        SubscriptionRepositoryAdapter subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenAdapter tokenPort
    ) {
        return authService(userRepository, apiKeyRepository, subscriptionRepository,
            passwordEncoder, tokenPort)::createApiKey;
    }

    @Bean
    public PaymentProcessorRegistry paymentProcessorRegistry(
        StripePaymentAdapter stripe,
        PayPalPaymentAdapter paypal,
        AdyenPaymentAdapter adyen,
        @Value("${payment.default-processor:stripe}") String defaultProcessor
    ) {
        return new PaymentProcessorRegistry(List.of(stripe, paypal, adyen), defaultProcessor);
    }

    @Bean
    public BillingService billingService(
        SubscriptionRepositoryAdapter subscriptionRepository,
        UserRepositoryAdapter userRepository,
        PaymentProcessorRegistry paymentProcessorRegistry,
        IdempotencyKeyRepositoryAdapter idempotencyKeyRepository,
        ProcessedWebhookEventRepositoryAdapter processedWebhookEventRepository
    ) {
        return new BillingService(
            subscriptionRepository, userRepository, paymentProcessorRegistry, idempotencyKeyRepository,
            processedWebhookEventRepository);
    }
}
