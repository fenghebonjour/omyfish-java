package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.port.out.PaymentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopPaymentProcessorRegistryTest {

    @Mock PaymentPort stripe;
    @Mock PaymentPort paypal;

    private void names() {
        lenient().when(stripe.name()).thenReturn("stripe");
        lenient().when(paypal.name()).thenReturn("paypal");
    }

    @Test
    void defaultProcessor_configuredDefault_isUsed() {
        names();
        when(stripe.isConfigured()).thenReturn(true);
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThat(registry.defaultProcessor()).contains(stripe);
    }

    @Test
    void defaultProcessor_unconfiguredDefault_fallsBackToNextConfiguredInPriorityOrder() {
        names();
        when(stripe.isConfigured()).thenReturn(false);
        when(paypal.isConfigured()).thenReturn(true);
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThat(registry.defaultProcessor()).contains(paypal);
    }

    @Test
    void defaultProcessor_noneConfigured_isEmpty() {
        names();
        when(stripe.isConfigured()).thenReturn(false);
        when(paypal.isConfigured()).thenReturn(false);
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThat(registry.defaultProcessor()).isEmpty();
    }

    @Test
    void byName_unconfigured_throws() {
        names();
        when(stripe.isConfigured()).thenReturn(false);
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThatThrownBy(() -> registry.byName("stripe")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void byName_unknown_throws() {
        names();
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThatThrownBy(() -> registry.byName("adyen")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exists_trueForKnownNameRegardlessOfConfiguration() {
        names();
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThat(registry.exists("stripe")).isTrue();
        assertThat(registry.exists("adyen")).isFalse();
    }

    @Test
    void configured_returnsOnlyThoseActuallyUsable() {
        names();
        when(stripe.isConfigured()).thenReturn(true);
        when(paypal.isConfigured()).thenReturn(false);
        ShopPaymentProcessorRegistry registry = new ShopPaymentProcessorRegistry(List.of(stripe, paypal), "stripe");

        assertThat(registry.configured()).containsExactly(stripe);
    }
}
