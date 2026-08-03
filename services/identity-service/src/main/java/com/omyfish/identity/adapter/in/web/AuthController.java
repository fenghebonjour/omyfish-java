package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.adapter.in.web.dto.*;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_FIELD_LENGTH = 255;

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
        validateRegistration(request);
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
            return ResponseEntity.ok(new AuthResponse(
                result.token(), result.refreshToken(), result.userId(), result.email(), result.role()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        try {
            var result = refreshTokenUseCase.refresh(request.refreshToken());
            return ResponseEntity.ok(new AuthResponse(
                result.token(), result.refreshToken(), result.userId(), result.email(), result.role()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @GetMapping("/auth/me")
    public ResponseEntity<MeResponse> me(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        var user = requireCaller(authHeader);
        return ResponseEntity.ok(new MeResponse(user.userId(), user.email(), user.role()));
    }

    private void validateRegistration(RegisterRequest request) {
        if (request.email() == null || !EMAIL.matcher(request.email()).matches()
            || request.email().length() > MAX_FIELD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH
            || request.password().length() > MAX_FIELD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_FIELD_LENGTH + " characters");
        }
        if (request.displayName() != null && request.displayName().length() > MAX_FIELD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is too long");
        }
    }

    private GetCurrentUserUseCase.CurrentUser requireCaller(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        try {
            return getCurrentUserUseCase.me(authHeader.substring(7));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/users/{userId}/api-keys")
    public ResponseEntity<ApiKeyResponse> createApiKey(
        @PathVariable UUID userId,
        @RequestBody ApiKeyRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        var caller = requireCaller(authHeader);
        if (!caller.userId().equals(userId) && !"ADMIN".equalsIgnoreCase(caller.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create API keys for another user");
        }
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
    record RefreshRequest(String refreshToken) {}
    record MeResponse(UUID userId, String email, String role) {}
}
