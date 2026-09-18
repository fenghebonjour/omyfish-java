package com.omyfish.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Regression coverage for §1.1 (gateway must default-deny, not just configure auth) and §1.3's
// refresh-token check — this class had zero tests before (WEAKNESS_AUDIT.md §4).
class AuthFilterTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";

    private final MockEnvironment environment = new MockEnvironment();
    private final AuthFilter filter = new AuthFilter(SECRET, environment);
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @Test
    void publicPath_bypassesAuthCheckEntirely() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange exchange = exchangeFor("/api/v1/species/identify", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void protectedPath_noAuthorizationHeader_returns401AndNeverCallsChain() {
        ServerWebExchange exchange = exchangeFor("/api/v1/observations", null);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void protectedPath_malformedToken_returns401() {
        ServerWebExchange exchange = exchangeFor("/api/v1/observations", "Bearer not-a-real-jwt");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void protectedPath_refreshTokenRejected_evenThoughSignatureIsValid() {
        String token = tokenFor(UUID.randomUUID(), "a@b.com", "USER", "refresh");
        ServerWebExchange exchange = exchangeFor("/api/v1/observations", "Bearer " + token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void protectedPath_validAccessToken_forwardsWithUserHeaders() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId, "angler@omyfish.io", "USER", null);
        ServerWebExchange exchange = exchangeFor("/api/v1/observations", "Bearer " + token);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo(userId.toString());
        assertThat(forwarded.getFirst("X-User-Email")).isEqualTo("angler@omyfish.io");
        assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void constructor_prodProfileWithDefaultDevSecret_throws() {
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AuthFilter("dev-secret-change-in-production-32b", environment))
            .isInstanceOf(IllegalStateException.class);
    }

    private ServerWebExchange exchangeFor(String path, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (authorizationHeader != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private String tokenFor(UUID userId, String email, String role, String tokenType) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        var builder = Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 60_000));
        if (tokenType != null) {
            builder.claim("token_type", tokenType);
        }
        return builder.signWith(key).compact();
    }
}
