package com.omyfish.identity.domain.port.out;

/** A request with the same Idempotency-Key is already in flight (or crashed without cleanup). */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
