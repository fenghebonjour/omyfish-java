package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.adapter.in.web.dto.*;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final CreateApiKeyUseCase createApiKeyUseCase;

    public AuthController(
        RegisterUseCase registerUseCase,
        LoginUseCase loginUseCase,
        CreateApiKeyUseCase createApiKeyUseCase
    ) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.createApiKeyUseCase = createApiKeyUseCase;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        try {
            var result = registerUseCase.register(
                new RegisterUseCase.RegisterCommand(request.email(), request.password())
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
            return ResponseEntity.ok(
                new AuthResponse(result.token(), result.userId(), result.email(), result.role())
            );
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
}
