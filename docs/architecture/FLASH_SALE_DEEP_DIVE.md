# Flash Sale Deep Dive: How Omyshop Survives Black Friday

A companion to `docs/BEGINNER_GUIDE.md`, focused on one feature slice: `shop-service` hardened
for a 2,000 QPS Black Friday flash sale (`docs/QPS2000_FlashSale.md`). Every mechanism below is
real code in this repo, built and load-tested across milestones M1–M7. Each section follows the
same shape: **the problem**, **the code** (file/line map), a **line-by-line walkthrough**, and
**plain CRUD, for contrast** — what a service without this hardening would do instead, and why
that breaks at scale.

Several sections end with a **Real bug found** box. These aren't hypothetical "gotchas" —
they're mistakes this exact hardening effort made and caught by refusing to trust that code
"should" work without proving it live. Reading those is arguably more valuable than the happy
path: it's what debugging this stuff actually looks like.

---

## Table of Contents

1. [Atomic stock decrement (no more lost updates)](#1-atomic-stock-decrement)
2. [Idempotency keys (no more duplicate orders)](#2-idempotency-keys)
3. [Undertow + XNIO (the web server swap)](#3-undertow--xnio)
4. [HikariCP sizing](#4-hikaricp-sizing)
5. [Jackson Afterburner](#5-jackson-afterburner)
6. [Multi-level caching (Caffeine + Redis)](#6-multi-level-caching)
7. [Postgres read replica + read/write splitting](#7-postgres-read-replica)
8. [Kafka event stream](#8-kafka-event-stream)
9. [Resilience4j: Circuit Breaker, Retry, Bulkhead, Rate Limiter](#9-resilience4j)
10. [Feature flag kill switch](#10-feature-flag-kill-switch)
11. [Observability: custom metrics, Grafana, JFR](#11-observability)
12. [Kubernetes HPA](#12-kubernetes-hpa)
13. [Load testing](#13-load-testing)
14. [Summary: what actually changed](#14-summary)

---

## 1. Atomic stock decrement

**The problem.** The obvious way to decrement stock is: load the product, check
`stockQuantity >= requestedQty` in Java, subtract, save. Under concurrency this is a classic
*lost update*: two requests both load `stockQuantity = 1`, both decide "yes, enough stock," both
decrement to 0, and you've sold the same last unit twice. At flash-sale concurrency this isn't
a rare edge case — it's the normal case for any popular item.

**The code.**

| What | File:line |
|---|---|
| Domain port | `domain/port/out/ProductRepository.java` — `tryDecreaseStock(UUID, int): boolean` |
| The atomic SQL | `adapter/out/persistence/ProductJpaRepository.java:14-17` |
| Caller | `application/service/ReserveOrderService.java:47-48` |

```java
// ProductJpaRepository.java:14-17
@Modifying
@Query("UPDATE ProductJpaEntity p SET p.stockQuantity = p.stockQuantity - :qty " +
       "WHERE p.id = :id AND p.stockQuantity >= :qty")
int decreaseStockIfAvailable(@Param("id") UUID id, @Param("qty") int qty);
```

**Walkthrough.** The `WHERE p.stockQuantity >= :qty` clause is the whole trick: the database
evaluates the check and the decrement as one atomic operation, under the row lock Postgres
takes for the `UPDATE` anyway. Two concurrent requests for the last unit no longer race on a
Java-level "read, think, write" — the *second* request's `UPDATE` simply matches zero rows
(because by the time it runs, `stockQuantity` is already below what it needs), and
`decreaseStockIfAvailable` returns `0`. `ReserveOrderService.java:47` checks that return value:

```java
// ReserveOrderService.java:47-48
if (!productRepository.tryDecreaseStock(product.getId(), item.quantity())) {
    throw new InsufficientStockException(product.getId(), item.quantity(), product.getStockQuantity());
}
```

No explicit lock, no `SELECT ... FOR UPDATE`, no optimistic-locking version column and retry
loop — the single `UPDATE` statement *is* the lock, for exactly as long as it needs to be. This
was proven correct under real load in M7: 9,600 concurrent checkout attempts against 50 units
of stock produced **exactly 50 successful orders and stock at exactly 0**, never negative
(`docs/architecture/flash-sale/LOAD_TEST_RESULTS.md`, Scenario 1).

**Plain CRUD, for contrast.** A typical CRUD tutorial writes:
```java
Product p = repo.findById(id);
if (p.getStock() >= qty) { p.setStock(p.getStock() - qty); repo.save(p); }
```
This works in every manual test, because a human can't click fast enough to race themselves.
It oversells under real concurrent load — not as a bug that occasionally fires, but as a
structural certainty once two requests for the same item land within the same read-then-write
window, which at flash-sale traffic is constant.

---

## 2. Idempotency keys

**The problem.** A client retry — a timeout, a double-tap, a flaky mobile connection — resends
the exact same `POST /checkout`. Without protection, that's a second stock decrement and a
second order for one customer's one purchase. This is a *correctness* bug, not a performance
one, and it was a real pre-existing gap in `shop-service` before this hardening (identity-service's
billing module already had this pattern, built after a real 2026-10-10 double-billing incident
— it simply hadn't been ported to Omyshop's own checkout yet).

**The code.**

| What | File:line |
|---|---|
| Required header | `adapter/in/web/CheckoutController.java:43` — `Idempotency-Key` |
| Reserve → replay → complete | `application/service/CheckoutOrchestrationService.java:59-107` |
| Unique constraint | `db/migration/V9__add_idempotency_keys.sql` — `UNIQUE(idempotency_key, endpoint)` |

**Walkthrough.** Three cases, all handled by the same ~50 lines
(`CheckoutOrchestrationService.java:59-107`):

```java
Optional<IdempotencyRecord> existing = idempotencyKeys.find(idempotencyKey, IdempotencyRecord.CHECKOUT);
if (existing.isPresent() && !isOrphaned(existing.get())) {
    return replayedCheckout(existing.get(), ownerKey);          // case 1: seen this key before
}
...
IdempotencyRecord reservation = existing.isPresent()
    ? requireOwner(existing.get(), ownerKey)                    // case 2: a stale reservation — reclaim it
    : idempotencyKeys.reserve(idempotencyKey, IdempotencyRecord.CHECKOUT, ownerKey);  // case 3: brand new
```

- **Case 1 — replay**: the key already has a *completed* record. Return the original
  `orderId`/`checkoutUrl` without touching stock again. This is what makes a client's retry
  safe: same key in, same response out, no side effect repeated.
- **Case 2 — a race on the same key**: two requests with the *same* key arrive concurrently. The
  `UNIQUE(idempotency_key, endpoint)` constraint means only one `reserve()` call can win; the
  loser gets an `IdempotencyConflictException` → HTTP 409, not a silent double-charge.
  `CheckoutResult` even carries a `replayed` boolean (`domain/port/in/CheckoutUseCase.java:11`) so
  the metrics layer (§11) can tell a genuine new checkout from a replay.
  - **Orphan handling**: a reservation that's been sitting incomplete for over a minute
    (`STALE_RESERVATION_AGE`, `CheckoutOrchestrationService.java:31`) is assumed abandoned (the
    process that made it probably crashed mid-flight) and gets reclaimed rather than permanently
    blocking that key.
- **Case 3 — new checkout**: reserve the key, run the real reserve→pay flow, then
  `reservation.completeCheckout(...)`. If anything downstream throws, the `catch` block at the
  bottom of `checkout()` deletes the reservation — so a genuinely failed attempt (not a retry of
  a successful one) can be retried fresh with the same key, instead of being permanently stuck.

**Plain CRUD, for contrast.** No idempotency key at all — `POST /checkout` just runs the flow
every time it's called. The failure mode isn't visible in a demo (nobody manually double-clicks
in testing), which is exactly why it's dangerous: it ships, works in every manual test, and then
double-charges real customers the first time a mobile network hiccups during Black Friday.

---

## 3. Undertow + XNIO

**The problem.** Tomcat (Spring Boot's default) uses one OS thread per connection. At 2,000
concurrent connections that's 2,000 threads, each costing real memory just to exist (default
Java thread stacks are ~512KB–1MB) and real OS-scheduling overhead switching between them.

**The code.**

| What | File:line |
|---|---|
| Dependency swap | `pom.xml:40-50` — excludes `spring-boot-starter-tomcat`, adds `spring-boot-starter-undertow` |
| Thread pool sizing | `application.yml:99-103` |

```yaml
# application.yml:99-103
undertow:
  threads:
    io: 8
    worker: 64
  direct-buffers: true
```

**Walkthrough.** Undertow's XNIO is non-blocking: a small pool of **I/O threads** (`io: 8`, one
to two per CPU core — their only job is reading/writing sockets, never blocking) hands actual
request *processing* off to a separate, much larger **worker thread pool** (`worker: 64`).
Worker threads are the ones that run this app's Spring MVC dispatch and its blocking JDBC calls
— that's exactly why the worker pool needs to be bigger: anything doing a blocking DB call
occupies a worker thread for the call's duration, same cost model as Tomcat, but confined to the
smaller pool of threads actually doing work, while thousands of idle/slow connections cost
almost nothing on the I/O side. `direct-buffers: true` uses off-heap `ByteBuffer`s for socket
I/O, avoiding a JVM-heap copy per read/write.

> **Real bug found**: Undertow's worker pool doesn't report its own busy-thread count by
> default (see §11) — a genuine blind spot in the metrics story, not a config typo, caught only
> by building the dashboard and noticing the gauge read a permanent `-1`.

**Plain CRUD, for contrast.** Leaving the default Tomcat connector in place "just works" at low
traffic and silently becomes the memory/thread-exhaustion bottleneck first, once traffic
climbs — and because it degrades gradually (more memory pressure, more context-switching, not
an outright crash), it's the kind of problem that's invisible until the one day it matters most.

---

## 4. HikariCP sizing

**The problem.** A connection pool sized for "whatever the default is" either starves under
load (too small — requests queue for a connection that's never coming fast enough) or exhausts
the database's own `max_connections` (too big, especially multiplied across replicas).

**The code.** `application.yml:9-23` (primary), `application.yml:31-38` (replica, §7).

```yaml
# application.yml
hikari:
  maximum-pool-size: 20
  minimum-idle: 20
  connection-timeout: 3000
  leak-detection-threshold: 30000
```

**Walkthrough.** `minimum-idle` equals `maximum-pool-size` — HikariCP's own documented guidance
for a pool expected to run under sustained load, avoiding the overhead of growing/shrinking the
pool mid-spike (growing a pool means opening new physical connections *during* the traffic
spike that made you need them). `connection-timeout: 3000` is the deliberate choice to **fail
fast** instead of queueing a request indefinitely: a request that can't get a connection within
3 seconds gets a clear failure immediately, which is what lets Resilience4j's circuit breaker
(§9) actually see failures and open, instead of every request just hanging silently until some
much longer default timeout. `leak-detection-threshold: 30000` logs a warning if a connection is
checked out for longer than 30s without being returned — the single most common real-world
cause of "pool exhausted" incidents is a connection leak, not actually needing more connections.

**Plain CRUD, for contrast.** Leaving Hikari at its own defaults (`maximum-pool-size: 10`) means
the pool itself becomes the ceiling on throughput long before the database or the app code is
the bottleneck — and a default multi-minute connection timeout means that ceiling fails by
*hanging*, not by a clean, fast error.

---

## 5. Jackson Afterburner

**The problem.** Jackson's default (de)serialization uses reflection to read/write each field.
At high request volume, that per-field reflection cost adds up across every JSON response.

**The code.** `config/AppConfig.java:39-42` registers the module; `pom.xml` adds
`jackson-module-afterburner`.

```java
// AppConfig.java:39-42
@Bean
AfterburnerModule afterburnerModule() {
    return new AfterburnerModule();
}
```

**Walkthrough.** Afterburner generates bytecode at startup that reads/writes each DTO's fields
*directly*, bypassing reflection on every subsequent call. Spring Boot's
`JacksonAutoConfiguration` automatically picks up any `com.fasterxml.jackson.databind.Module`
bean and registers it on the shared `ObjectMapper` — the one line above is the entire
integration.

> **Real bug found — the most significant one in this whole effort.** Afterburner's generated
> bytecode runs under its *own* `ClassLoader`
> (`config/CachedProduct.java`'s doc comment explains this in detail). `CachedProduct` — the DTO
> `ProductCache` (§6) uses to (de)serialize into Redis — was originally package-private, not
> `public`. A package-private class is a *different runtime package* to Afterburner's
> ClassLoader even with a matching package name, so every attempt to (de)serialize it through
> Redis threw `IllegalAccessError` — caught by `ProductCache`'s existing try/catch, logged as a
> harmless-looking WARN, and silently falling through to the database every single time. The
> system was never *incorrect* — that fallback is exactly what it's there for — but the entire
> point of the Redis (L2) tier, letting one replica's cache warming every other replica, was a
> complete no-op from the moment it was built until this bug was found and fixed (making
> `CachedProduct` `public`) while building the cache's own hit/miss metrics (§11) — the metric
> was the thing that made the silent failure visible at all.

**Plain CRUD, for contrast.** No Afterburner: correct, just paying reflection overhead on every
(de)serialization, which only shows up as a measurable cost at real scale — not something a
functional test would ever catch either way.

---

## 6. Multi-level caching

**The problem.** At flash-sale QPS, every request hitting Postgres directly for product data
would overwhelm it — caching is mandatory, but caching has its own three classic failure modes:
**avalanche** (many cache entries expire at the same instant, and the resulting thundering herd
all miss together), **penetration** (repeated lookups of something that doesn't exist, which
caching can't help with directly since there's never anything to cache), and **stampede**
(many concurrent requests for the *same* key all miss at once and all hit the database
simultaneously).

**The code.** `config/ProductCache.java`, `config/CachingGetProductUseCase.java`,
`config/CachingListProductsUseCase.java`.

| Concern | File:line |
|---|---|
| L1 (Caffeine, in-process) | `ProductCache.java:65-66` |
| L2 (Redis, shared) | `ProductCache.java:101`, `:129` |
| Negative cache (penetration) | `ProductCache.java:47,70,74,78` |
| Jittered TTL (avalanche) | `ProductCache.java:201-224` |
| Graceful Redis-outage fallback | `ProductCache.java:` every Redis call wrapped in try/catch |

**Walkthrough — the two tiers.** `getProduct`/`getList` check the in-process Caffeine cache
first (no network hop — the fast path that matters at 2,000 QPS); on a miss there, they check
Redis (shared across every replica, so N pods behind the K8s HPA don't each independently
re-populate their own cold L1 from Postgres); only a miss at *both* tiers reaches the database.

**Walkthrough — penetration mitigation.** `l1Missing` (`ProductCache.java:56`) is a small,
short-TTL (`NEGATIVE_TTL = 10s`, line 47) Caffeine cache of *known-nonexistent* IDs.
`isKnownMissing`/`markMissing` (lines 74/78) let `CachingGetProductUseCase` short-circuit a
repeated lookup of a bad ID without ever reaching Postgres — deliberately Caffeine-only, no
Redis tier, since a miss here is already cheap (one indexed PK lookup) and not worth sharing
across replicas at this catalog's size.

**Walkthrough — avalanche mitigation.** `jitteredExpiry` (lines 211-224) doesn't give every
cache entry the same TTL — it adds a random delta (`±L1_JITTER`) at creation time, so entries
written around the same moment (e.g. right after a deploy, when every L1 is cold) don't all
expire in the same instant and stampede Postgres together. `jitteredTtl` (line 201) does the
same thing for Redis's own `SET ... EX` TTL.

**Walkthrough — the graceful-fallback story.** Every single Redis operation
(`readRawFromRedis`, `writeToRedis`, `deleteFromRedis`) is wrapped in its own try/catch that
logs a WARN and returns `null`/no-ops — doing by hand what a Spring `CacheErrorHandler` would do
for declarative `@Cacheable` usage (this cache uses direct, programmatic access instead, for
simpler, more explicit error handling). This was proven live in M7 under a real, running Redis
outage mid-load-test: **0% error rate throughout**, with the actual cost of the outage visible
only as a jump in how many concurrent k6 virtual users were needed to sustain the same request
rate — slower, never wrong (`LOAD_TEST_RESULTS.md`, Scenario 3).

**On cache stampede**: at this catalog's scale (10 SKUs, a handful of cached keys total) a
request-coalescing layer (e.g. a per-key lock so only one request repopulates a hot key while
others wait on it) would be over-engineering — flagged here as the one failure mode that's
*mitigated by scale* rather than by code, not overlooked.

**Plain CRUD, for contrast.** No cache: every single request round-trips to Postgres. This is
simplest to reason about and the first thing to fall over the moment real traffic arrives — M7
proved the *cached* read path sustains 2,000 req/s at sub-millisecond p90 latency; without this
layer, that number is bounded by Postgres's own connection pool and query latency instead, which
is dramatically lower.

---

## 7. Postgres read replica

**The problem.** Even with caching absorbing most traffic, cache misses and any future
read-heavy feature still compete with writes (the stock decrement, order creation) for the
same database connections and the same I/O.

**The code.** `config/DataSourceConfig.java`, `config/ReadWriteRoutingDataSource.java`,
`config/ReadOnlyRoutingAspect.java`, `config/ReadOnlyGetProductUseCase.java`,
`config/ReadOnlyListProductsUseCase.java`.

**Walkthrough.** `DataSourceConfig` wires up two real `HikariDataSource` beans (`primary`,
`replica`) plus a `@Primary DataSource` that's actually a `ReadWriteRoutingDataSource`
(extends Spring's `AbstractRoutingDataSource`) wrapping both. `ReadOnlyGetProductUseCase` and
`ReadOnlyListProductsUseCase` are thin `@Transactional(readOnly = true)` wrappers inserted
between the plain `ProductService` and the caching decorators (§6) in `AppConfig` — so a cache
*miss* for a product read ends up on the replica, while every write stays on the primary by
default.

> **Real bug found — a genuine, non-obvious Spring+JPA incompatibility.** The textbook way to
> drive `AbstractRoutingDataSource` is to check
> `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` inside
> `determineCurrentLookupKey()`. That's what this code originally did, and it silently never
> worked — every read went to the primary regardless. The reason, found only by adding
> temporary diagnostic logging at three call sites and comparing timestamps to the millisecond:
> `JpaTransactionManager.doBegin()` (`ReadWriteRoutingDataSource.java:9-14`'s own comment)
> acquires the *physical JDBC connection* — to apply the readOnly hint to it — **before**
> `AbstractPlatformTransactionManager` ever sets that flag on the synchronization manager. By
> the time `determineCurrentLookupKey()` would see "yes, this is read-only," the connection has
> already been chosen. The fix: a `ThreadLocal` (`ReadWriteRoutingDataSource.java:21`), set not
> by the transaction itself but by a dedicated `@Aspect` ordered at
> `Ordered.HIGHEST_PRECEDENCE` (`ReadOnlyRoutingAspect.java:32`) — high enough precedence that it
> wraps *outside* Spring's own transactional advice (which defaults to the lowest precedence),
> so the marker is set before the transaction (and its connection acquisition) ever begins.
> Confirmed live by literally stopping the replica container mid-test and watching reads fail
> immediately — proof the routing was real — then adding a `NestedRuntimeException` catch in the
> aspect (`ReadOnlyRoutingAspect.java:42`) so a dead replica retries against the primary instead
> of failing the request outright.

**Plain CRUD, for contrast.** One datasource, every query — reads and writes genuinely compete
for the same connections and the same disk I/O. Fine until read volume grows past what the
cache absorbs; this repo deliberately didn't rebuild this same replica inside the K8s deployment
(§12) too, since re-proving the same lesson there would double that slice's complexity for no
additional teaching value.

---

## 8. Kafka event stream

**The problem.** The raw spec asked for "an asynchronous inventory reduction mechanism...to
decouple the transaction database from the immediate request lifecycle." The actual decoupling
that matters here is narrower and more honest: the stock decrement itself is already fast and
correct (§1, a single atomic `UPDATE`) — what genuinely benefits from being asynchronous is
everything *downstream* of a successful, paid order: sending a confirmation email, and building
a replayable audit trail. Neither of those should block the checkout response or the payment
webhook.

**The code.** `shared/omyfish-shared-events/.../OrderPlacedEvent.java`,
`adapter/out/messaging/KafkaOrderEventPublisher.java`,
`adapter/in/messaging/OrderEventLedgerConsumer.java` (shop-service's own consumer),
`notification-service/.../consumer/OrderPlacedConsumer.java`.

**Walkthrough — why Kafka, additive, not a RabbitMQ replacement.** This repo already runs
RabbitMQ for `omyfish.species`/`omyfish.observations`, untouched by this work. Kafka was chosen
*specifically* for this one new stream on its technical merits for this particular shape of
problem: **partition-by-key ordering** for a hot SKU
(`OrderPlacedEvent.java:26` — `partitionKey()` returns the first line's `productId`, so every
event for the same product stays ordered through one consumer, not scattered across consumers
racing each other), **log replay** (a consumer group can be reset to re-process history — the
actual mechanism behind "replayable audit ledger"), and **consumer-group scaling** that lines up
naturally with the K8s HPA (§12) scaling the pods that host these consumers.

**Walkthrough — when the event fires.** `CheckoutOrchestrationService` publishes
`OrderPlacedEvent` from the **payment-confirmed webhook handler**, not at the moment of
reservation — a deliberate deviation from a more literal reading of "right after reservation."
Firing at reservation time would mean a "confirmation" email and an audit-ledger row for an
order whose payment then fails or is abandoned on the processor's own page; firing on confirmed
payment means both downstream consumers only ever see orders that actually happened.

**Walkthrough — two independent consumer groups, same topic.** `shop-service`'s own
`OrderEventLedgerConsumer` (group `shop-service-ledger`) builds a replayable audit table;
`notification-service`'s `OrderPlacedConsumer` (group `notification-service`) sends the
in-app confirmation. Separate consumer groups mean a slow email send can never hold up the audit
write, or vice versa — each group gets its own copy of every message and progresses
independently. Both dedup against Kafka's at-least-once redelivery using the event's own
`eventId` (the ledger keys on `(event_id, product_id)` since one event carries multiple order
lines; notification keys on `source_event_id` alone, reusing the exact same pattern the
RabbitMQ consumers already used for their own redelivery dedup).

**Plain CRUD, for contrast.** Sending the confirmation email and writing an audit row inline,
in the same request/webhook thread that confirms payment: simplest code, and now a slow SMTP
call or a slow audit-table write directly adds to the response latency of confirming a payment —
and a transient failure in either one has no natural retry/replay story beyond "hope the
request gets retried," which it usually won't, since the webhook already succeeded from the
payment processor's point of view.

---

## 9. Resilience4j

**The problem.** A flash sale means payment processors occasionally fail, slow down, or rate-limit — and without protection, every one of those failures either cascades (every checkout thread blocks waiting on a dying processor) or gets retried in a way that risks a double-charge.

**The code.** `application.yml:141-210`; annotations on
`StripeCheckoutAdapter.java:66-68,116-118` (and identically on the PayPal/Adyen/mock adapters);
the admission-control rate limiter on `adapter/in/web/CheckoutController.java` (`@RateLimiter`).

```java
// StripeCheckoutAdapter.java:66-68
@Retry(name = "stripe")
@CircuitBreaker(name = "stripe")
@Bulkhead(name = "stripe")
public Optional<CheckoutSession> createCheckoutSession(...)
```

**Walkthrough — four mechanisms, four different jobs:**
- **Retry** (`application.yml` `resilience4j.retry.configs.payment-gateway`): 3 attempts,
  exponential backoff with jitter. Safe here *specifically* because every adapter passes its
  `idempotencyKey` through to the processor's own idempotency mechanism (Stripe's
  `Idempotency-Key`, PayPal's `PayPal-Request-Id`, Adyen's `RequestOptions` key) — a retried
  attempt replays the processor's original result instead of double-charging.
- **Circuit Breaker**: opens after a failure-rate threshold (50% over a 10-call sliding window)
  and stops calling a reliably-failing processor for 30s. This is what stops every concurrent
  checkout thread from each individually paying the full timeout cost of a dying payment
  processor — once it's open, failures return instantly instead of waiting out a timeout.
- **Bulkhead**: caps concurrent calls into one processor (20) so a slow processor can't starve
  the entire worker thread pool (§3) by holding all of it hostage waiting on one dependency.
- **Rate Limiter** (a *different* instance, `checkout`, on the controller, not the adapter):
  admission control on `/checkout` itself — 50 requests/second, `timeout-duration: 0` meaning an
  over-limit caller is rejected immediately (429) instead of queued. This is deliberately
  separate from the three above: it protects *this service* from being overwhelmed by its own
  callers, independent of whether the payment processor is healthy. Each processor gets its own
  CB/Retry/Bulkhead instance rather than one shared instance across all three, specifically so
  Stripe going down doesn't also trip the breaker guarding PayPal/Adyen, which checkout could
  still fall back to.

Proven under real load in M7: 9,600 checkout attempts at 80/s against a 50/s limiter — ~3,571
were rejected at the limiter before ever reaching application code, and the remaining 6,029 that
got through were correctly subject to the stock check (§1), producing exactly 50 successes
(`LOAD_TEST_RESULTS.md`, Scenario 1).

**Plain CRUD, for contrast.** A plain `RestTemplate`/`HttpClient` call to the payment processor,
no annotations: one slow or failing processor call blocks its calling thread for the full
default timeout, every concurrent checkout does the same, and the worker pool (§3) fills with
threads all stuck waiting on the same dying dependency — the textbook cascading failure.

---

## 10. Feature flag kill switch

**The problem.** If the flash sale's checkout path is misbehaving in a way that needs
investigation, redeploying to disable it costs minutes that matter when real money is moving.

**The code.** `config/FeatureFlags.java` (24 lines total), checked in
`CheckoutController.java` before any other checkout logic runs.

**Walkthrough.** `checkoutEnabled` is a `volatile boolean`, flipped via an admin-only endpoint
(`adapter/in/web/AdminFeatureFlagController.java`). `CheckoutController` checks it *first*,
before touching the rate limiter, the cart, or anything else — a 503 "Checkout is temporarily
paused" costs nothing. The class's own comment is explicit about its own limitation: in-memory,
one instance, doesn't survive a restart and isn't shared across replicas — a real production
version of this would live in Redis (already in this stack for §6) so the flag is instant and
consistent across every pod behind the K8s HPA (§12), not per-instance.

**Plain CRUD, for contrast.** No kill switch: the only way to stop a misbehaving checkout path
is a redeploy (minutes) or manually scaling replicas to zero (which also stops serving the
*healthy* parts of the API).

---

## 11. Observability

**The problem.** None of the above claims ("sustains 2,000 QPS," "the circuit breaker engages,"
"the cache fallback works") are trustworthy without a way to actually measure them, live.

**The code.**

| What | File:line |
|---|---|
| Checkout outcome counter | `config/MeteredCheckoutUseCase.java:14,30,36` |
| Cache hit/miss counter | `config/ProductCache.java:139-142` |
| Undertow worker-pool gauges | `config/UndertowMetricsBinder.java:27-43` |
| Grafana dashboard | `infrastructure/grafana/provisioning/dashboards/flash-sale.json` |
| JFR toggle | `Dockerfile:32` |

**Walkthrough — why these are hand-rolled, not `@Cacheable`/automatic.** `ProductCache` uses
direct, programmatic cache access rather than Spring's declarative `@Cacheable`, so Micrometer's
automatic cache-metrics registrar doesn't apply — `recordCacheResult` (`ProductCache.java:139`)
is that same hit/miss ratio, recorded by hand with the same three-outcome shape (`l1_hit`,
`l2_hit`, `miss`) a real dashboard panel needs. Similarly, `MeteredCheckoutUseCase` is a thin
adapter-layer decorator (same pattern as the Transactional/Caching wrappers elsewhere in
`config/`) wrapping `CheckoutUseCase`, recording `result=new|replayed|rejected` — deliberately
*not* inside `application/service`, which stays free of Spring/Micrometer imports per this
repo's hexagonal rule.

> **Real bug found (twice) building the Undertow gauges.** Spring Boot ships no actuator
> metrics binder for Undertow's XNIO worker pool at all (unlike Tomcat/Jetty) — the whole reason
> `UndertowMetricsBinder` exists. Building it surfaced two separate, genuine problems: (1) the
> worker pool/MXBean only exist once the embedded server has actually *started*, which is why
> this is an `ApplicationListener<WebServerInitializedEvent>` rather than a bean built at wiring
> time; and (2) the busy-thread gauge read a permanent `-1` even once wired correctly — traced
> (via `javap` on the actual XNIO/JBoss-Threads jars, not guessing) to
> `EnhancedQueueExecutor.getActiveCount()` being gated behind a JVM system property
> (`jboss.threads.eqe.statistics.active-count`) that's off by default because the tracking has a
> real per-task cost. Fixed with that flag on the JVM command line (`Dockerfile:32`).
> A metric that doesn't exist is an obvious gap; a metric that exists and silently always reads
> `-1` is the more dangerous failure mode, since it looks like working instrumentation.

**Plain CRUD, for contrast.** Shipping without any of this: every claim in this document — the
2,000 QPS number, the cache fallback working, the rate limiter engaging — would be an assertion,
not a measurement. The load test results (§13) exist specifically because this instrumentation
does.

---

## 12. Kubernetes HPA

**The problem.** Fixed replica counts mean either over-provisioning for the peak (wasted cost
every other day of the year) or under-provisioning for it (the one day it matters most).

**The code.** `docs/k8s-practice/manifests/flash-sale/06-shop-service.yaml:58-76`.

```yaml
# 06-shop-service.yaml:58-76
kind: HorizontalPodAutoscaler
spec:
  minReplicas: 2
  maxReplicas: 8
  metrics:
  - type: Resource
    resource:
      name: cpu
      target: { type: Utilization, averageUtilization: 50 }
```

**Walkthrough.** CPU-based, deliberately, not the original spec's custom request-rate metric —
a custom metric needs `prometheus-adapter` configured on top of a full `kube-prometheus-stack`
install, a notably heavier and more fragile piece of infrastructure than the HPA mechanism
itself for a demo of this scope. CPU utilization against `requests.cpu: 200m` (set deliberately
modest, specifically so real load pushes utilization past 50% without needing production-scale
traffic to prove the mechanism) still requires `metrics-server` — which doesn't ship with `kind`
by default and needs a `--kubelet-insecure-tls` patch to work on a local cluster at all. Proven
live, not simulated: a real load generator pushed CPU to 254% of the 50% target, and the HPA
scaled shop-service from 2 replicas to the configured maximum of 8, then back down to 2 over
the following ~4 minutes once the load stopped (`behavior.scaleDown.stabilizationWindowSeconds:
60` is what paces that scale-down, avoiding flapping on a momentary dip).

> **Real bugs found getting this cluster stable** (full detail in `docs/K8S_PRACTICE_PLAN.md`'s
> Section 7 notes): a bare `:latest` image tag silently defaults `imagePullPolicy` to `Always`,
> which kept re-pulling a since-restricted image from Docker Hub even after it was loaded into
> the node by hand; a single-node Kafka broker crash-looped because its *own* self-registration
> RPC, routed through the Service's ClusterIP instead of straight to loopback, added just enough
> kube-proxy latency to blow past Kafka's internal timeout; and a `ServiceMonitor` matched
> nothing at first because its selector matches the **Service object's own labels**, not the
> `spec.selector` that Service uses to find its pods — an easy, non-obvious mix-up.

**Plain CRUD, for contrast.** A fixed `replicas: 2` Deployment with no HPA: survives normal
traffic, falls over at the first real spike with no automatic response, and the only fix is a
human manually running `kubectl scale` after already noticing the problem.

---

## 13. Load testing

**The problem.** Every claim above needs to be tested against real, generated concurrent
traffic — not a single manual `curl`, and not a claim of "should work."

**The code.** `services/shop-service/loadtest/` — `scripts/{ramp-up,step-spike,cache-failure}.js`
(k6, run via the official `grafana/k6` Docker image, no host install needed),
`adapter/out/payment/MockPaymentCheckoutAdapter.java` + a WireMock container (via
`docker-compose.loadtest.yml`) standing in for a real payment gateway at realistic latency.

**Walkthrough — the honesty discipline.** The full results and methodology are in
`docs/architecture/flash-sale/LOAD_TEST_RESULTS.md`; the short version: the read path (product
catalog) is the genuine "2,000 QPS" claim (proven, both as a sustained plateau and as a step
spike, at 0% errors), while checkout is *deliberately* not claimed at 2,000 QPS, since it's
rate-limited to 50/s by design (§9) and bounded by actual inventory (§1) — claiming otherwise
would misrepresent what the system is actually built to do. The cache-failure scenario measured
the *cost* of a real Redis outage (more concurrent virtual users needed to sustain the same
throughput) rather than just asserting "it still works," because "slower but correct" and "fast
and broken" both deserve to be visible in the numbers, not flattened into a single pass/fail.

**Plain CRUD, for contrast.** Shipping "hardened" code with no load test: every mechanism above
would be exactly as true as it was before anyone ran a single concurrent request against it,
which is to say, unverified.

---

## 14. Summary

What actually changed, start to finish:

| Layer | Before | After |
|---|---|---|
| Stock decrement | read-check-write (races, oversells) | atomic conditional `UPDATE` |
| Checkout retries | duplicate orders | idempotency keys, replay-safe |
| Web server | Tomcat, thread-per-connection | Undertow/XNIO, non-blocking |
| Connection pool | HikariCP defaults | sized + fail-fast |
| JSON (de)serialization | reflection | Afterburner-generated bytecode |
| Product reads | hit Postgres every time | Caffeine (L1) + Redis (L2), jittered TTLs |
| DB read scaling | one instance for everything | routed replica for cache-miss overflow |
| Post-payment side effects | inline, blocking the webhook response | Kafka, two independent consumer groups |
| Payment-adapter failures | cascade, hang the whole worker pool | Retry + Circuit Breaker + Bulkhead per processor |
| Admission under overload | none | rate limiter, fail-fast 429 |
| Emergency stop | redeploy | in-memory kill switch |
| "Is it actually working" | assumed | Micrometer counters/gauges + Grafana + JFR |
| Scaling | fixed replica count | HPA, proven live under real load |
| "Does it survive 2,000 QPS" | untested claim | k6 load test, honest numbers, both ways |

The single most important thing this document can leave you with isn't any one mechanism above —
it's the discipline that produced the "Real bug found" boxes: nearly every one of them was
caught not by reading the code more carefully, but by refusing to accept "should work" and
instead stopping a real dependency, generating real concurrent load, or reading the actual
decompiled bytecode of a third-party library to see what a metric call really does. Code review
finds typos. Only running the thing — adversarially, on purpose — finds the kind of bug that
looks completely fine until the moment it matters.
