package com.omyfish.species.domain.exception;

/** The ai-service call failed or returned an unusable response. */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
