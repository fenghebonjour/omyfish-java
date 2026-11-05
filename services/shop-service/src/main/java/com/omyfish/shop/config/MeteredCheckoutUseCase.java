package com.omyfish.shop.config;

import com.omyfish.shop.domain.model.valueobject.CartOwner;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Records checkout attempts/outcomes for Grafana (docs/architecture/flash-sale dashboard) —
 * a cross-cutting adapter-layer concern, same reason the Transactional- and Caching- wrappers
 * live here rather than in application/service, which stays free of Spring/Micrometer imports.
 */
class MeteredCheckoutUseCase implements CheckoutUseCase {

    private static final String METRIC = "shop.checkout.requests";

    private final CheckoutUseCase delegate;
    private final MeterRegistry registry;

    MeteredCheckoutUseCase(CheckoutUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public CheckoutResult checkout(
        CartOwner owner, String guestEmail, String successUrl, String cancelUrl, String idempotencyKey
    ) {
        try {
            CheckoutResult result = delegate.checkout(owner, guestEmail, successUrl, cancelUrl, idempotencyKey);
            registry.counter(METRIC, "result", result.replayed() ? "replayed" : "new").increment();
            return result;
        } catch (RuntimeException e) {
            // Covers IdempotencyConflictException (a genuine same-key race), insufficient
            // stock, and any adapter failure CheckoutOrchestrationService maps to a thrown
            // exception — all of them are a rejected attempt from the caller's point of view.
            registry.counter(METRIC, "result", "rejected").increment();
            throw e;
        }
    }
}
