package com.omyfish.identity.domain.exception;

/** A payment-provider webhook could not be authenticated or parsed. */
public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException(String message) {
        super(message);
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
