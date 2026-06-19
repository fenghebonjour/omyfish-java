package com.omyfish.identity.application.service;

import com.omyfish.identity.domain.model.User;
import com.omyfish.identity.domain.port.in.LoginUseCase.LoginCommand;
import com.omyfish.identity.domain.port.in.RegisterUseCase.RegisterCommand;
import com.omyfish.identity.domain.port.out.ApiKeyRepository;
import com.omyfish.identity.domain.port.out.TokenPort;
import com.omyfish.identity.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock ApiKeyRepository apiKeyRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenPort tokenPort;
    @InjectMocks AuthService authService;

    @Test
    void register_newEmail_createsUserAndReturnsResult() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        User saved = User.create("alice@example.com", "hashed", "USER");
        when(userRepository.save(any())).thenReturn(saved);

        var result = authService.register(new RegisterCommand("alice@example.com", "secret"));

        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.userId()).isNotNull();
        verify(passwordEncoder).encode("secret");
        verify(userRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
            authService.register(new RegisterCommand("alice@example.com", "secret"))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsToken() {
        User user = User.create("alice@example.com", "hashed", "USER");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.issue(any(UUID.class), eq("alice@example.com"), eq("USER"))).thenReturn("jwt.token.here");

        var result = authService.login(new LoginCommand("alice@example.com", "secret"));

        assertThat(result.token()).isEqualTo("jwt.token.here");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void login_wrongPassword_throwsIllegalArgument() {
        User user = User.create("alice@example.com", "hashed", "USER");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() ->
            authService.login(new LoginCommand("alice@example.com", "wrong"))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_unknownEmail_throwsIllegalArgument() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            authService.login(new LoginCommand("nobody@example.com", "any"))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
