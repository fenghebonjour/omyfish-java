package com.omyfish.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Zero coverage of this filter before (WEAKNESS_AUDIT.md §4) — including its own §1.2 limits.
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @Test
    void unthrottledPath_alwaysPassesThroughRegardlessOfVolume() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 50; i++) {
            filter.filter(exchangeFor("/api/v1/observations", "1.1.1.1"), chain).block();
        }

        verify(chain, times(50)).filter(any());
    }

    @Test
    void identify_tenthRequestFromSameIpStillAllowed_eleventhRejected() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 10; i++) {
            ServerWebExchange exchange = exchangeFor("/api/v1/species/identify", "2.2.2.2");
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        ServerWebExchange eleventh = exchangeFor("/api/v1/species/identify", "2.2.2.2");
        filter.filter(eleventh, chain).block();

        assertThat(eleventh.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, times(10)).filter(any());
    }

    @Test
    void biteScore_thirtyFirstRequestFromSameIpRejected() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 30; i++) {
            filter.filter(exchangeFor("/api/v1/species/bite-score/forecast", "3.3.3.3"), chain).block();
        }

        ServerWebExchange thirtyFirst = exchangeFor("/api/v1/species/bite-score/today", "3.3.3.3");
        filter.filter(thirtyFirst, chain).block();

        assertThat(thirtyFirst.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, times(30)).filter(any());
    }

    @Test
    void identify_limitIsPerIp_secondIpUnaffectedBySaturatedFirstIp() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 10; i++) {
            filter.filter(exchangeFor("/api/v1/species/identify", "4.4.4.4"), chain).block();
        }
        ServerWebExchange saturatedIp = exchangeFor("/api/v1/species/identify", "4.4.4.4");
        filter.filter(saturatedIp, chain).block();
        assertThat(saturatedIp.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        ServerWebExchange freshIp = exchangeFor("/api/v1/species/identify", "5.5.5.5");
        filter.filter(freshIp, chain).block();

        assertThat(freshIp.getResponse().getStatusCode()).isNull();
    }

    private ServerWebExchange exchangeFor(String path, String remoteIp) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
            .remoteAddress(new InetSocketAddress(remoteIp, 12345))
            .build();
        return MockServerWebExchange.from(request);
    }
}
