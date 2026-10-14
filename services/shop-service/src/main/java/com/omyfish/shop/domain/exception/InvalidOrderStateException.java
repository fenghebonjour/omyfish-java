package com.omyfish.shop.domain.exception;

import java.util.UUID;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(UUID orderId, String reason) {
        super("Order " + orderId + " cannot perform this action: " + reason);
    }
}
