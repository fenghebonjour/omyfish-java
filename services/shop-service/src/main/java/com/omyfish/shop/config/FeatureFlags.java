package com.omyfish.shop.config;

import org.springframework.stereotype.Component;

/**
 * Emergency manual switch to pause checkout instantly without a redeploy — e.g. if the flash
 * sale's inventory or payment path is misbehaving and needs to be paused while it's
 * investigated. In-memory for now (one instance, no restart survival); once Redis is
 * introduced for caching, this should move there so the flag is shared across replicas and
 * survives a restart instead of defaulting back to enabled.
 */
@Component
public class FeatureFlags {

    private volatile boolean checkoutEnabled = true;

    public boolean isCheckoutEnabled() {
        return checkoutEnabled;
    }

    public void setCheckoutEnabled(boolean checkoutEnabled) {
        this.checkoutEnabled = checkoutEnabled;
    }
}
