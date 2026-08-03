package com.omyfish.identity.adapter.in.web.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Extraction of the bearer credentials from an {@code Authorization} header. */
public final class BearerTokens {

    private static final String PREFIX = "Bearer ";

    private BearerTokens() {
    }

    /** @throws ResponseStatusException 401 when the header is absent or not a bearer header */
    public static String require(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return authorizationHeader.substring(PREFIX.length());
    }
}
