package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.adapter.in.web.dto.*;
import com.omyfish.identity.adapter.in.web.support.BearerTokens;
import com.omyfish.identity.adapter.in.web.support.HttpErrors;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final CreateApiKeyUseCase createApiKeyUseCase;

    public AuthController(
        RegisterUseCase registerUseCase,
        LoginUseCase loginUseCase,
        RefreshTokenUseCase refreshTokenUseCase,
        GetCurrentUserUseCase getCurrentUserUseCase,
        CreateApiKeyUseCase createApiKeyUseCase
    ) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.createApiKeyUseCase = createApiKeyUseCase;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        var result = HttpErrors.mapIllegalArgumentTo(HttpStatus.CONFLICT, () -> registerUseCase.register(
            new RegisterUseCase.RegisterCommand(request.email(), request.password(), request.displayName())
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RegisterResponse(result.userId(), result.email()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        var result = HttpErrors.mapIllegalArgumentTo(HttpStatus.UNAUTHORIZED, () -> loginUseCase.login(
            new LoginUseCase.LoginCommand(request.email(), request.password())
        ));
        return ResponseEntity.ok(new AuthResponse(
            result.token(), result.refreshToken(), result.userId(), result.email(), result.role()
        ));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        var result = HttpErrors.mapIllegalArgumentTo(
            HttpStatus.UNAUTHORIZED, () -> refreshTokenUseCase.refresh(request.refreshToken()));
        return ResponseEntity.ok(new AuthResponse(
            result.token(), result.refreshToken(), result.userId(), result.email(), result.role()
        ));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<MeResponse> me(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String token = BearerTokens.require(authHeader);
        var user = HttpErrors.mapIllegalArgumentTo(HttpStatus.UNAUTHORIZED, () -> getCurrentUserUseCase.me(token));
        return ResponseEntity.ok(new MeResponse(user.userId(), user.email(), user.role()));
    }

    @PostMapping("/users/{userId}/api-keys")
    public ResponseEntity<ApiKeyResponse> createApiKey(
        @PathVariable UUID userId,
        @RequestBody ApiKeyRequest request
    ) {
        var result = HttpErrors.mapIllegalArgumentTo(HttpStatus.NOT_FOUND, () -> createApiKeyUseCase.createApiKey(
            new CreateApiKeyUseCase.CreateApiKeyCommand(userId, request.name())
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiKeyResponse(result.keyId(), result.plainKey(), result.name()));
    }

    @GetMapping("/auth/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    record ApiKeyRequest(String name) {}
    record RefreshRequest(String refreshToken) {}
    record MeResponse(UUID userId, String email, String role) {}
}
