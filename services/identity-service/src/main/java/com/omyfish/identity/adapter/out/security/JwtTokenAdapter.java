package com.omyfish.identity.adapter.out.security;

import com.omyfish.identity.domain.port.out.TokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements TokenPort {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenAdapter(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-ms}") long expirationMs,
        @Value("${jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Override
    public String issue(UUID userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(key)
            .compact();
    }

    @Override
    public String issueRefresh(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshExpirationMs))
            .signWith(key)
            .compact();
    }

    @Override
    public Optional<UUID> validateRefresh(String token) {
        return parse(token)
            .filter(c -> TOKEN_TYPE_REFRESH.equals(c.get(TOKEN_TYPE_CLAIM, String.class)))
            .map(c -> UUID.fromString(c.getSubject()));
    }

    @Override
    public Optional<UUID> validateAccess(String token) {
        // Access tokens carry no token_type claim; reject refresh tokens here.
        return parse(token)
            .filter(c -> c.get(TOKEN_TYPE_CLAIM) == null)
            .map(c -> UUID.fromString(c.getSubject()));
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
