package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.port.out.PaymentPort;

import java.util.List;
import java.util.Optional;

/**
 * Picks a processor for a new checkout (configured default, with fallback) and looks up the
 * specific processor an already-started subscription or webhook belongs to.
 */
public class PaymentProcessorRegistry {

    private final List<PaymentPort> processors;
    private final String defaultName;

    public PaymentProcessorRegistry(List<PaymentPort> processors, String defaultName) {
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
