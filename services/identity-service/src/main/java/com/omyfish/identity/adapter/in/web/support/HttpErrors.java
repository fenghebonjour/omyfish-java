package com.omyfish.identity.adapter.in.web.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/** Translation of domain-level validation failures into HTTP responses. */
public final class HttpErrors {

    private HttpErrors() {
    }

    /**
     * Runs {@code action}, turning the {@link IllegalArgumentException} the use
     * cases raise for rejected input into {@code status} with its message.
     */
    public static <T> T mapIllegalArgumentTo(HttpStatus status, Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(status, e.getMessage());
        }
    }
}
