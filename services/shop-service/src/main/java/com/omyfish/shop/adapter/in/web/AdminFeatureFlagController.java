package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.config.FeatureFlags;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Admin-only, same double-layered protection (gateway JWT + this role check) as
// AdminProductController — this path isn't in the gateway's PUBLIC_PREFIXES.
@RestController
@RequestMapping("/api/v1/shop/admin/feature-flags")
public class AdminFeatureFlagController {

    private final FeatureFlags featureFlags;

    public AdminFeatureFlagController(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    @PostMapping("/checkout")
    public Map<String, Boolean> setCheckoutEnabled(
        @RequestHeader("X-User-Role") String userRoleHeader, @RequestBody Map<String, Boolean> request
    ) {
        if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required");
        }
        featureFlags.setCheckoutEnabled(enabled);
        return Map.of("enabled", enabled);
    }
}
