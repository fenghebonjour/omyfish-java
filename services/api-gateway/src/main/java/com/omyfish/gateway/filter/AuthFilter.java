package com.omyfish.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    // All species endpoints (identify, bite-score, catalog browsing) are public,
    // matching the dotnet stack; observation GeoJSON is the public map feed.
    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/api/auth/",
        "/api/v1/species",
        "/api/v1/observations/geojson",
        "/api/billing/webhook"  // Stripe calls this; signature-verified in the service
    );

    private final SecretKey key;

    public AuthFilter(
        @Value("${jwt.secret}") String secret,
        org.springframework.core.env.Environment environment
    ) {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && secret.startsWith("dev-secret")) {
            throw new IllegalStateException(
                "JWT_SECRET must be set to a non-default value when the 'prod' profile is active.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authHeader.substring(7))
                .getPayload();

            // Refresh tokens are signed with the same key but must never
            // authenticate API calls — they are only valid at /api/auth/refresh.
            if ("refresh".equals(claims.get("token_type", String.class))) {
                return reject(exchange, HttpStatus.UNAUTHORIZED);
            }

            ServerWebExchange mutated = exchange.mutate()
                .request(r -> r
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Email", claims.get("email", String.class))
                    .header("X-User-Role", claims.get("role", String.class))
                )
                .build();

            return chain.filter(mutated);

        } catch (JwtException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
