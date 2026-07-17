package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.ApiKey;
import com.omyfish.identity.domain.model.User;
import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import com.omyfish.identity.domain.port.out.ApiKeyRepository;
import com.omyfish.identity.domain.port.out.TokenPort;
import com.omyfish.identity.domain.port.out.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

public class AuthService implements
    RegisterUseCase, LoginUseCase, RefreshTokenUseCase, GetCurrentUserUseCase, CreateApiKeyUseCase {

    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;

    public AuthService(
        UserRepository userRepository,
        ApiKeyRepository apiKeyRepository,
        PasswordEncoder passwordEncoder,
        TokenPort tokenPort
    ) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPort = tokenPort;
    }

    @Override
    public RegisterResult register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String hash = passwordEncoder.encode(command.password());
        User user = User.create(command.email(), hash, "USER");
        user = userRepository.save(user);
        return new RegisterResult(user.getId(), user.getEmail());
    }

    @Override
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = tokenPort.issue(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = tokenPort.issueRefresh(user.getId());
        return new LoginResult(token, refreshToken, user.getId(), user.getEmail(), user.getRole());
    }

    @Override
    public RefreshResult refresh(String refreshToken) {
        User user = tokenPort.validateRefresh(refreshToken)
            .flatMap(userRepository::findById)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        String token = tokenPort.issue(user.getId(), user.getEmail(), user.getRole());
        String rotated = tokenPort.issueRefresh(user.getId());
        return new RefreshResult(token, rotated, user.getId(), user.getEmail(), user.getRole());
    }

    @Override
    public CurrentUser me(String accessToken) {
        User user = tokenPort.validateAccess(accessToken)
            .flatMap(userRepository::findById)
            .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }

    @Override
    public CreateApiKeyResult createApiKey(CreateApiKeyCommand command) {
        userRepository.findById(command.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String plainKey = "omf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String keyHash = passwordEncoder.encode(plainKey);

        ApiKey apiKey = ApiKey.create(command.userId(), keyHash, command.name());
        apiKey = apiKeyRepository.save(apiKey);
        return new CreateApiKeyResult(apiKey.getId(), plainKey, apiKey.getName());
    }
}
