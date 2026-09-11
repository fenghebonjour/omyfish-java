package com.omyfish.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP fixed-window rate limiting on {@code /api/v1/species/identify} and
 * {@code /api/v1/species/bite-score/**} — both are public routes (see {@link AuthFilter}) that
 * drive cost on the external AI service, so without a limit here they were open to unbounded
 * free usage (BACKLOG.md item G, WEAKNESS_AUDIT.md §1.2, matching omyfish-dotnet's identical
 * identify/bite-score rate limits: 10/min and 30/min).
 *
 * <p>In-memory rather than Spring Cloud Gateway's Redis-backed {@code RequestRateLimiter} —
 * api-gateway runs as a single instance in this stack and no Redis exists here yet; revisit if
 * this service is ever scaled horizontally.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int IDENTIFY_LIMIT_PER_MINUTE = 10;
    private static final int BITE_SCORE_LIMIT_PER_MINUTE = 30;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, Window> identifyWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> biteScoreWindows = new ConcurrentHashMap<>();

    private record Window(long windowStart, AtomicInteger count) {
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        ConcurrentHashMap<String, Window> windows;
        int limit;
        if (path.equals("/api/v1/species/identify")) {
            windows = identifyWindows;
            limit = IDENTIFY_LIMIT_PER_MINUTE;
        } else if (path.startsWith("/api/v1/species/bite-score")) {
            windows = biteScoreWindows;
            limit = BITE_SCORE_LIMIT_PER_MINUTE;
        } else {
            return chain.filter(exchange);
        }

        int count = countRequest(windows, resolveClientIp(exchange));
        if (count > limit) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private int countRequest(ConcurrentHashMap<String, Window> windows, String clientIp) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW_MILLIS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
            ? remoteAddress.getAddress().getHostAddress()
            : "unknown";
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
