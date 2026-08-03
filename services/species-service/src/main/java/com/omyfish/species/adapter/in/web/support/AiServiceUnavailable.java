package com.omyfish.species.adapter.in.web.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/** 503 payload returned by the controllers proxying omyfish-ai when it is down. */
public final class AiServiceUnavailable {

    private AiServiceUnavailable() {
    }

    public static ResponseEntity<Map<String, String>> response(String detail) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "AI service unavailable", "detail", detail));
    }
}
