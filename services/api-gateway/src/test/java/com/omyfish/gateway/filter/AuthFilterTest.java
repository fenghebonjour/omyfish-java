package com.omyfish.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthFilterTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256-signing";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final AuthFilter filter = new AuthFilter(SECRET, new MockEnvironment());

    /** Captures the exchange handed downstream so header mutation can be asserted. */
    private static final class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }

        boolean wasCalled() { return captured != null; }

        ServerHttpRequest request() { return captured.getRequest(); }
    }

    private static String token(Map<String, ?> claims) {
        return Jwts.builder()
            .subject("11111111-1111-1111-1111-111111111111")
            .claims(claims)
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(KEY)
            .compact();
    }

    private static MockServerWebExchange exchange(String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void publicPrefixesSkipAuthentication() {
        for (String path : new String[]{
            "/api/v1/auth/login",
            "/api/v1/species",
            "/api/v1/species/bite-score/forecast",
            "/api/v1/observations/geojson",
            "/api/v1/billing/webhook"
        }) {
            CapturingChain chain = new CapturingChain();
            MockServerWebExchange exchange = exchange(path, null);

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).as(path).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).as(path).isNull();
        }
    }

    @Test
    void validAccessTokenForwardsUserHeaders() {
        CapturingChain chain = new CapturingChain();
        String jwt = token(Map.of("email", "angler@omyfish.io", "role", "USER"));

        filter.filter(exchange("/api/v1/observations", "Bearer " + jwt), chain).block();

        assertThat(chain.wasCalled()).isTrue();
        HttpHeaders headers = chain.request().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("angler@omyfish.io");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void refreshTokenIsRejected() {
        CapturingChain chain = new CapturingChain();
        String jwt = token(Map.of("email", "angler@omyfish.io", "role", "USER", "token_type", "refresh"));
        MockServerWebExchange exchange = exchange("/api/v1/observations", "Bearer " + jwt);

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingAuthorizationHeaderIsRejected() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = exchange("/api/v1/observations", null);

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonBearerAuthorizationHeaderIsRejected() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = exchange("/api/v1/observations", "Basic dXNlcjpwYXNz");

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        CapturingChain chain = new CapturingChain();
        String foreignJwt = Jwts.builder()
            .subject("someone")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor("a-completely-different-secret-key-value-32b".getBytes(StandardCharsets.UTF_8)))
            .compact();
        MockServerWebExchange exchange = exchange("/api/v1/observations", "Bearer " + foreignJwt);

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        CapturingChain chain = new CapturingChain();
        String expired = Jwts.builder()
            .subject("someone")
            .expiration(new Date(System.currentTimeMillis() - 60_000))
            .signWith(KEY)
            .compact();
        MockServerWebExchange exchange = exchange("/api/v1/observations", "Bearer " + expired);

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void defaultDevSecretIsRefusedUnderProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AuthFilter("dev-secret-change-me", prod))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    void runsBeforeOtherGatewayFilters() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}
