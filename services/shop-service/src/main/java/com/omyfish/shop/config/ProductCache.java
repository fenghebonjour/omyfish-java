package com.omyfish.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.omyfish.shop.domain.model.Product;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two-level read cache for the product catalog: an in-process Caffeine L1 (no network hop, the
 * fast path for 2,000 QPS) backed by a shared Redis L2 (so N replicas behind the K8s HPA don't
 * all independently hammer Postgres whenever their own L1 happens to be cold). Every Redis
 * operation is wrapped in try/catch — a Redis outage falls through to the real database (via
 * the caller) instead of throwing, the same job a Spring {@code CacheErrorHandler} would do for
 * declarative {@code @Cacheable} usage; this class uses the cache APIs directly instead, so it
 * handles that itself.
 *
 * <p><b>Known tradeoff:</b> the cached {@code stockQuantity} is a display value, not an
 * authoritative one — the actual flash-sale admission check ({@code ProductRepository.
 * tryDecreaseStock}) always hits Postgres directly and never reads this cache, so overselling
 * correctness is unaffected. A cancelled/returned order's restock also isn't wired to evict this
 * cache (that mutation happens below the use-case layer this cache wraps), so a restocked item's
 * displayed count can lag by up to L1_TTL+L1_JITTER. Both are accepted: the real decrement path
 * is unaffected, and this only skews what a browsing customer sees for well under a minute.
 */
@Component
class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);

    private static final Duration L1_TTL = Duration.ofSeconds(30);
    private static final Duration L1_JITTER = Duration.ofSeconds(5);
    private static final Duration L2_TTL = Duration.ofMinutes(5);
    private static final Duration L2_JITTER = Duration.ofMinutes(1);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(10);

    private static final String LIST_L1_KEY = "list";
    private static final String REDIS_PRODUCT_PREFIX = "shop:product:";
    private static final String REDIS_LIST_KEY = "shop:product-list";
    private static final String METRIC = "shop.cache.requests";

    private final Cache<String, CachedProduct> l1Products;
    private final Cache<String, List<CachedProduct>> l1List;
    private final Cache<String, Boolean> l1Missing;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry registry;

    ProductCache(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry registry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.l1Products = Caffeine.newBuilder().maximumSize(1_000).expireAfter(jitteredExpiry(L1_TTL, L1_JITTER)).build();
        this.l1List = Caffeine.newBuilder().maximumSize(10).expireAfter(jitteredExpiry(L1_TTL, L1_JITTER)).build();
        // Penetration mitigation (repeated lookups of a nonexistent id): Caffeine-only, no
        // Redis tier — a cache miss here just costs one cheap indexed PK lookup on whichever
        // pod takes it, not worth sharing across replicas at this catalog's size.
        this.l1Missing = Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(NEGATIVE_TTL).build();
    }

    boolean isKnownMissing(UUID id) {
        return l1Missing.getIfPresent(id.toString()) != null;
    }

    void markMissing(UUID id) {
        l1Missing.put(id.toString(), Boolean.TRUE);
    }

    Optional<Product> getProduct(UUID id) {
        String key = id.toString();
        CachedProduct cached = l1Products.getIfPresent(key);
        if (cached != null) {
            recordCacheResult("product", "l1_hit");
            return Optional.of(cached.toDomain());
        }
        CachedProduct fromRedis = readProductFromRedis(REDIS_PRODUCT_PREFIX + key);
        if (fromRedis != null) {
            l1Products.put(key, fromRedis);
            recordCacheResult("product", "l2_hit");
            return Optional.of(fromRedis.toDomain());
        }
        recordCacheResult("product", "miss");
        return Optional.empty();
    }

    void putProduct(Product product) {
        CachedProduct cached = CachedProduct.from(product);
        l1Products.put(product.getId().toString(), cached);
        writeToRedis(REDIS_PRODUCT_PREFIX + product.getId(), cached, L2_TTL, L2_JITTER);
    }

    void evictProduct(UUID id) {
        l1Products.invalidate(id.toString());
        deleteFromRedis(REDIS_PRODUCT_PREFIX + id);
        evictList();
    }

    Optional<List<Product>> getList() {
        List<CachedProduct> cached = l1List.getIfPresent(LIST_L1_KEY);
        if (cached != null) {
            recordCacheResult("list", "l1_hit");
        } else {
            cached = readListFromRedis();
            if (cached != null) {
                l1List.put(LIST_L1_KEY, cached);
                recordCacheResult("list", "l2_hit");
            } else {
                recordCacheResult("list", "miss");
            }
        }
        return cached == null ? Optional.empty() : Optional.of(cached.stream().map(CachedProduct::toDomain).toList());
    }

    void putList(List<Product> products) {
        List<CachedProduct> cached = products.stream().map(CachedProduct::from).toList();
        l1List.put(LIST_L1_KEY, cached);
        writeToRedis(REDIS_LIST_KEY, cached, L2_TTL, L2_JITTER);
    }

    void evictList() {
        l1List.invalidate(LIST_L1_KEY);
        deleteFromRedis(REDIS_LIST_KEY);
    }

    // Programmatic cache access (not @Cacheable), so Micrometer's automatic cache-metrics
    // registrar doesn't apply — this is that same hit/miss ratio, recorded by hand.
    private void recordCacheResult(String cache, String result) {
        registry.counter(METRIC, "cache", cache, "result", result).increment();
    }

    private static final TypeReference<List<CachedProduct>> LIST_TYPE = new TypeReference<>() {
    };

    private CachedProduct readProductFromRedis(String key) {
        String json = readRawFromRedis(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CachedProduct.class);
        } catch (Exception e) {
            log.warn("Redis value for key {} could not be parsed — falling through to the database", key, e);
            return null;
        }
    }

    private List<CachedProduct> readListFromRedis() {
        String json = readRawFromRedis(REDIS_LIST_KEY);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("Redis value for key {} could not be parsed — falling through to the database",
                REDIS_LIST_KEY, e);
            return null;
        }
    }

    private String readRawFromRedis(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key {} — falling through to the database", key, e);
            return null;
        }
    }

    private void writeToRedis(String key, Object value, Duration baseTtl, Duration jitter) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, jitteredTtl(baseTtl, jitter));
        } catch (Exception e) {
            log.warn("Redis write failed for key {} — serving stays correct from L1/the database, "
                + "just without a shared L2 entry for other replicas", key, e);
        }
    }

    private void deleteFromRedis(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete failed for key {} — that entry will keep serving stale data "
                + "from L2 until its TTL expires", key, e);
        }
    }

    private static Duration jitteredTtl(Duration base, Duration jitter) {
        long jitterSeconds = jitter.getSeconds();
        long delta = jitterSeconds == 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitterSeconds, jitterSeconds + 1);
        return base.plusSeconds(delta);
    }

    // expireAfterCreate randomizes each entry's total lifetime so entries written around the
    // same time (e.g. right after a deploy, every L1 cold) don't all expire in the same instant
    // and stampede Redis/Postgres together (cache avalanche). expireAfterUpdate/Read just keep
    // whatever duration was already assigned.
    private static <K, V> Expiry<K, V> jitteredExpiry(Duration base, Duration jitter) {
        long baseNanos = base.toNanos();
        long jitterNanos = jitter.toNanos();
        return new Expiry<K, V>() {
            @Override
            public long expireAfterCreate(K key, V value, long currentTime) {
                long delta = jitterNanos == 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitterNanos, jitterNanos + 1);
                return Math.max(1, baseNanos + delta);
            }

            @Override
            public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }
}
