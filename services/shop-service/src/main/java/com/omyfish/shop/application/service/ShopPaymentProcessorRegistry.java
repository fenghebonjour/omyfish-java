package com.omyfish.shop.application.service;

import com.omyfish.shop.domain.port.out.PaymentPort;

import java.util.List;
import java.util.Optional;

/**
 * Picks a processor for a new checkout (configured default, with fallback) and looks up the
 * specific processor an already-started order belongs to. Same shape as identity-service's
 * PaymentProcessorRegistry — not shared code, since the two services' PaymentPort interfaces
 * are deliberately separate (one-shot checkout vs. subscriptions; see PaymentPort.java's own
 * doc comment).
 */
public class ShopPaymentProcessorRegistry {

    private final List<PaymentPort> processors;
    private final String defaultName;

    public ShopPaymentProcessorRegistry(List<PaymentPort> processors, String defaultName) {
        this.processors = processors;
        this.defaultName = defaultName;
    }

    /** The configured default if it's usable, else the first configured processor in priority order. */
    public Optional<PaymentPort> defaultProcessor() {
        return findByName(defaultName)
            .filter(PaymentPort::isConfigured)
            .or(() -> processors.stream().filter(PaymentPort::isConfigured).findFirst());
    }

    /** Whether a processor with this name is registered at all, configured or not. */
    public boolean exists(String name) {
        return findByName(name).isPresent();
    }

    /** Every processor that's actually usable right now, in priority order. */
    public List<PaymentPort> configured() {
        return processors.stream().filter(PaymentPort::isConfigured).toList();
    }

    /** Looks up a processor by name. Throws if it's unknown or not configured — a corrupt-state case. */
    public PaymentPort byName(String name) {
        return findByName(name)
            .filter(PaymentPort::isConfigured)
            .orElseThrow(() -> new IllegalStateException("Unknown or unconfigured payment processor: " + name));
    }

    private Optional<PaymentPort> findByName(String name) {
        return processors.stream().filter(p -> p.name().equals(name)).findFirst();
    }
}
