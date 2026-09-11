package com.omyfish.identity.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Catches any exception a controller's own {@code @ExceptionHandler} doesn't already turn into
 * clean JSON, so callers get a structured 500 instead of Spring Boot's default unstructured
 * error response (BACKLOG.md item G, WEAKNESS_AUDIT.md §2.2).
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} rather than relying only on a bare
 * {@code @ExceptionHandler(Exception.class)} — Spring's own framework-level exceptions (a
 * missing required header, an unreadable request body, failed {@code @Valid} binding, etc.)
 * need their existing status codes preserved. Without the base class's inherited handlers in
 * the resolution set, a blanket {@code Exception.class} method below would intercept those
 * first and turn e.g. a missing-header 400 into a generic 500 — confirmed by a real test
 * failure in observation-service's equivalent handler before this base class was added there.
 *
 * <p>{@link ResponseStatusException} gets its own handler so it keeps the status/message the
 * controller intended (register/login/refresh throw these deliberately for 401/404/409) —
 * without it, the broad {@code Exception.class} handler below would intercept it first and turn
 * every intentional 401 into a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An unexpected error occurred."));
    }
}
