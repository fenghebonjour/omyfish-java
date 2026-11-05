package com.omyfish.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.shop.domain.model.Product;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private ProductCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        cache = new ProductCache(redis, new ObjectMapper(), new SimpleMeterRegistry());
    }

    private static Product product(UUID id) {
        return Product.reconstitute(id, "Jig", "desc", 999, "img.jpg", 10, true);
    }

    @Test
    void getProduct_totalMiss_returnsEmptyWithoutThrowing() {
        UUID id = UUID.randomUUID();
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(cache.getProduct(id)).isEmpty();
    }

    @Test
    void putThenGet_servesFromL1WithoutTouchingRedisAgain() {
        UUID id = UUID.randomUUID();
        cache.putProduct(product(id));
        // putProduct itself writes to Redis once; a subsequent read should come from L1 only.
        clearInvocations(valueOps);

        var result = cache.getProduct(id);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Jig");
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void getProduct_l1MissL2Hit_promotesToL1() throws Exception {
        UUID id = UUID.randomUUID();
        String json = new ObjectMapper().writeValueAsString(CachedProduct.from(product(id)));
        when(valueOps.get("shop:product:" + id)).thenReturn(json);

        var first = cache.getProduct(id);
        clearInvocations(valueOps);
        var second = cache.getProduct(id);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void getProduct_redisThrows_fallsThroughToEmptyInsteadOfPropagating() {
        UUID id = UUID.randomUUID();
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(cache.getProduct(id)).isEmpty();
    }

    @Test
    void putProduct_redisThrows_stillPopulatesL1() {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("redis down")).when(valueOps).set(anyString(), anyString(), any());

        cache.putProduct(product(id));
        clearInvocations(valueOps);
        var result = cache.getProduct(id);

        assertThat(result).isPresent();
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void markMissing_thenIsKnownMissing_isTrue() {
        UUID id = UUID.randomUUID();

        assertThat(cache.isKnownMissing(id)).isFalse();
        cache.markMissing(id);
        assertThat(cache.isKnownMissing(id)).isTrue();
    }

    @Test
    void evictProduct_clearsL1AndDeletesFromRedis() {
        UUID id = UUID.randomUUID();
        cache.putProduct(product(id));

        cache.evictProduct(id);
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(cache.getProduct(id)).isEmpty();
        verify(redis).delete("shop:product:" + id);
        verify(redis).delete("shop:product-list");
    }

    @Test
    void putListThenGetList_servesFromL1() {
        UUID id = UUID.randomUUID();
        cache.putList(List.of(product(id)));
        clearInvocations(valueOps);

        var result = cache.getList();

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void getList_totalMiss_returnsEmpty() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(cache.getList()).isEmpty();
    }
}
