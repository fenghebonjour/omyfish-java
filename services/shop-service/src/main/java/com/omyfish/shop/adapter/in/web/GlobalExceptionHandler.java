package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.domain.exception.CartEmptyException;
import com.omyfish.shop.domain.exception.InsufficientStockException;
import com.omyfish.shop.domain.exception.InvalidOrderStateException;
import com.omyfish.shop.domain.exception.OrderNotFoundException;
import com.omyfish.shop.domain.exception.ProductNotFoundException;
import com.omyfish.shop.domain.port.out.IdempotencyConflictException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

// Same structured-error idiom as observation-service's GlobalExceptionHandler.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CartEmptyException.class)
    public ResponseEntity<Map<String, String>> handleCartEmpty(CartEmptyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({
        InsufficientStockException.class, InvalidOrderStateException.class, IdempotencyConflictException.class
    })
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    // Thrown when an Idempotency-Key is replayed with a different cart owner than the one that
    // first reserved it — not a stock/order-state conflict, so it gets its own 400 rather than
    // joining handleConflict's 409s.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    // The @RateLimiter on CheckoutController.checkout() throws this once the instance's
    // admission-control cap is hit — a 429 tells the client to back off and retry shortly,
    // distinct from the 503s below (which mean the dependency itself is unhealthy).
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Map<String, String>> handleRateLimited(RequestNotPermitted e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("error", "Too many requests right now — please try again shortly"));
    }

    // CheckoutOrchestrationService throws this when no payment processor is configured, or when
    // Stripe checkout-session creation itself failed — application/ can't throw a Spring
    // ResponseStatusException directly, so it's mapped to 503 here instead.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handlePaymentUnavailable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

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
