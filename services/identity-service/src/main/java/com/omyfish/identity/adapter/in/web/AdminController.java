package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.adapter.in.web.support.BearerTokens;
import com.omyfish.identity.adapter.in.web.support.HttpErrors;
import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.out.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final BillingService billing;
    private final UserRepository users;
    private final GetCurrentUserUseCase currentUser;

    public AdminController(BillingService billing, UserRepository users,
                           GetCurrentUserUseCase currentUser) {
        this.billing = billing;
        this.users = users;
        this.currentUser = currentUser;
    }

    private void requireAdmin(String authHeader) {
        String token = BearerTokens.require(authHeader);
        var user = HttpErrors.mapIllegalArgumentTo(HttpStatus.UNAUTHORIZED, () -> currentUser.me(token));
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    @GetMapping("/stats")
    public BillingService.Stats stats(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        requireAdmin(authHeader);
        return billing.stats();
    }

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> subscriptions(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        requireAdmin(authHeader);
        return billing.allSubscriptions().stream().map(this::row).toList();
    }

    @PostMapping("/subscriptions/{userId}/grant")
    public Map<String, Object> grant(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID userId,
        @RequestBody(required = false) GrantRequest request
    ) {
        requireAdmin(authHeader);
        return row(billing.grant(userId,
            request != null && request.plan() != null ? request.plan() : "yearly",
            request != null && request.days() != null ? request.days() : 365));
    }

    @PostMapping("/subscriptions/{userId}/revoke")
    public Map<String, Object> revoke(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID userId
    ) {
        requireAdmin(authHeader);
        return row(HttpErrors.mapIllegalArgumentTo(HttpStatus.NOT_FOUND, () -> billing.revoke(userId)));
    }

    @PostMapping("/subscriptions/{userId}/extend-trial")
    public Map<String, Object> extendTrial(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID userId,
        @RequestBody(required = false) GrantRequest request
    ) {
        requireAdmin(authHeader);
        return row(billing.extendTrial(userId,
            request != null && request.days() != null ? request.days() : 7));
    }

    private Map<String, Object> row(Subscription s) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("userId", s.getUserId());
        map.put("email", users.findById(s.getUserId())
            .map(u -> u.getEmail()).orElse("?"));
        map.put("status", s.getEffectiveStatus());
        map.put("plan", s.getPlan());
        map.put("trialEnd", s.getTrialEnd());
        map.put("currentPeriodEnd", s.getCurrentPeriodEnd());
        return map;
    }

    record GrantRequest(Integer days, String plan) {}
}
