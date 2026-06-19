package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.domain.port.in.CreateApiKeyUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase;
import com.omyfish.identity.domain.port.in.LoginUseCase.LoginResult;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockBean RegisterUseCase registerUseCase;
    @MockBean LoginUseCase loginUseCase;
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
    void login_validCredentials_returnsToken() throws Exception {
        when(loginUseCase.login(any()))
            .thenReturn(new LoginResult("jwt.token.here", UUID.randomUUID(), "alice@example.com", "USER"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY_VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt.token.here"))
            .andExpect(jsonPath("$.role").value("USER"));
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
