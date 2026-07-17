package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase;
import com.omyfish.identity.domain.port.in.GetCurrentUserUseCase.CurrentUser;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase.LoginResult;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase;
import com.omyfish.identity.domain.port.in.RefreshTokenUseCase.RefreshResult;
import com.omyfish.identity.domain.port.in.RegisterUseCase;
import com.omyfish.identity.domain.port.in.RegisterUseCase.RegisterResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockBean RegisterUseCase registerUseCase;
    @MockBean LoginUseCase loginUseCase;
    @MockBean RefreshTokenUseCase refreshTokenUseCase;
    @MockBean GetCurrentUserUseCase getCurrentUserUseCase;
    @MockBean CreateApiKeyUseCase createApiKeyUseCase;

    private static final String REGISTER_BODY =
        "{\"email\":\"alice@example.com\",\"password\":\"password123\"}";
    private static final String LOGIN_BODY_VALID =
        "{\"email\":\"alice@example.com\",\"password\":\"password123\"}";
    private static final String LOGIN_BODY_WRONG =
        "{\"email\":\"alice@example.com\",\"password\":\"wrong\"}";

    @Test
    void register_returnsCreated() throws Exception {
        when(registerUseCase.register(any()))
            .thenReturn(new RegisterResult(UUID.randomUUID(), "alice@example.com"));

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        when(registerUseCase.register(any()))
            .thenThrow(new IllegalArgumentException("Email already registered"));

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsTokenPair() throws Exception {
        when(loginUseCase.login(any()))
            .thenReturn(new LoginResult(
                "jwt.token.here", "jwt.refresh.here", UUID.randomUUID(), "alice@example.com", "USER"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY_VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt.token.here"))
            .andExpect(jsonPath("$.refreshToken").value("jwt.refresh.here"))
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void refresh_validToken_returnsNewPair() throws Exception {
        when(refreshTokenUseCase.refresh("jwt.refresh.here"))
            .thenReturn(new RefreshResult(
                "jwt.token.new", "jwt.refresh.new", UUID.randomUUID(), "alice@example.com", "USER"));

        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"jwt.refresh.here\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt.token.new"))
            .andExpect(jsonPath("$.refreshToken").value("jwt.refresh.new"));
    }

    @Test
    void refresh_invalidToken_returnsUnauthorized() throws Exception {
        when(refreshTokenUseCase.refresh(anyString()))
            .thenThrow(new IllegalArgumentException("Invalid refresh token"));

        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"bogus\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_validToken_returnsUser() throws Exception {
        when(getCurrentUserUseCase.me("jwt.token.here"))
            .thenReturn(new CurrentUser(UUID.randomUUID(), "alice@example.com", "USER"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer jwt.token.here"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void me_missingHeader_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        when(loginUseCase.login(any()))
            .thenThrow(new IllegalArgumentException("Invalid credentials"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY_WRONG))
            .andExpect(status().isUnauthorized());
    }
}
