package com.omyfish.identity.config;

import com.omyfish.identity.adapter.out.payment.StripePaymentAdapter;
import com.omyfish.identity.adapter.out.persistence.ApiKeyRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.SubscriptionRepositoryAdapter;
import com.omyfish.identity.adapter.out.persistence.UserRepositoryAdapter;
import com.omyfish.identity.adapter.out.security.JwtTokenAdapter;
import com.omyfish.identity.application.service.AuthService;
import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    public BillingService billingService(
        SubscriptionRepositoryAdapter subscriptionRepository,
        UserRepositoryAdapter userRepository,
        StripePaymentAdapter paymentPort
    ) {
        return new BillingService(subscriptionRepository, userRepository, paymentPort);
    }
}
