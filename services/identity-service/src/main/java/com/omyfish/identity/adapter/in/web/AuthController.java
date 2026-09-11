package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.adapter.in.web.dto.*;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    // Refresh token now travels only as an httpOnly, SameSite=Strict cookie scoped to
    // /api/v1/auth, never in the response body — a stolen 30-day token via XSS was a
    // long-lived account takeover (BACKLOG.md item G, WEAKNESS_AUDIT.md §1.3, matching
    // omyfish-dotnet's identical fix). Secure requires HTTPS, so it's off outside the 'prod'
    // profile to keep local/docker-compose dev (plain HTTP) working.
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final CreateApiKeyUseCase createApiKeyUseCase;
    private final boolean cookieSecure;

    public AuthController(
        RegisterUseCase registerUseCase,
        LoginUseCase loginUseCase,
        RefreshTokenUseCase refreshTokenUseCase,
        GetCurrentUserUseCase getCurrentUserUseCase,
        CreateApiKeyUseCase createApiKeyUseCase,
        Environment environment
    ) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.createApiKeyUseCase = createApiKeyUseCase;
        this.cookieSecure = Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        try {
            var result = registerUseCase.register(
                new RegisterUseCase.RegisterCommand(request.email(), request.password(), request.displayName())
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(result.userId(), result.email()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            var result = loginUseCase.login(
                new LoginUseCase.LoginCommand(request.email(), request.password())
            );
            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new AuthResponse(result.token(), result.userId(), result.email(), result.role()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh cookie");
        }
        try {
            var result = refreshTokenUseCase.refresh(refreshToken);
            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new AuthResponse(result.token(), result.userId(), result.email(), result.role()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(0)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cleared.toString())
            .build();
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(REFRESH_COOKIE_MAX_AGE)
            .build();
    }

    @GetMapping("/auth/me")
    public ResponseEntity<MeResponse> me(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        try {
            var user = getCurrentUserUseCase.me(authHeader.substring(7));
            return ResponseEntity.ok(new MeResponse(user.userId(), user.email(), user.role()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/users/{userId}/api-keys")
    public ResponseEntity<ApiKeyResponse> createApiKey(
        @PathVariable UUID userId,
        @RequestBody ApiKeyRequest request
    ) {
        try {
            var result = createApiKeyUseCase.createApiKey(
                new CreateApiKeyUseCase.CreateApiKeyCommand(userId, request.name())
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiKeyResponse(result.keyId(), result.plainKey(), result.name()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/auth/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    record ApiKeyRequest(String name) {}
    record MeResponse(UUID userId, String email, String role) {}
}
