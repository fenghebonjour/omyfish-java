# OMyFish Java — Weakness Audit (learning notes)

Ported from `omyfish-dotnet/docs/WEAKNESS_AUDIT.md`'s senior-dev-style review
(2026-09-10) — see that file for the original writeup and fix snippets.
This repo is a hexagonal-architecture microservices backend like dotnet, so
most findings translate directly; a few are already handled correctly by
existing Spring/Flyway conventions. Tracked for real work in `BACKLOG.md`
item G — this file is the "why", that file is the "what to do".

---

## 1. Security

**Status: fixed and verified 2026-09-11** for the three items that applied
(§1.2, §1.3, §1.4). §1.1 was already fine.

### 1.1 Gateway auth enforcement — already fine

**Not applicable — already correct.** `AuthFilter` (a Spring Cloud Gateway
`GlobalFilter`, auto-applied to every route — there's no separate opt-in
step like ASP.NET's `.RequireAuthorization()`) is default-deny: it checks an
explicit `PUBLIC_PREFIXES` allowlist first and rejects everything else
without a valid, non-refresh-typed JWT. This is the architecturally correct
pattern dotnet's fix introduced — already implemented here.

### 1.2 No rate limiting on `/api/v1/species/**`

**Problem:** the whole species surface (identify, bite-score, regs) is
public per `AuthFilter`'s allowlist, same as dotnet's `AllowAnonymous`
identify endpoint, but no rate limiter existed anywhere — no
bucket4j/resilience4j, no Spring Cloud Gateway `RequestRateLimiter` config.
Unbounded free access to the paid AI backend.

**Fix:** a small in-memory, per-IP, fixed-window `GlobalFilter`
(`RateLimitFilter`) — **not** Spring Cloud Gateway's Redis-backed
`RequestRateLimiter`, since no Redis exists in this stack and api-gateway
runs as a single instance here; standing up Redis just for this would be
disproportionate. Same rates as dotnet: `/api/v1/species/identify` at
10/min, `/api/v1/species/bite-score/**` at 30/min. Revisit with
`RedisRateLimiter` if the gateway is ever scaled horizontally.

### 1.3 Refresh token in response body + `localStorage`

**Problem:** `AuthResponse` returned `refreshToken` in the JSON body from
both `/auth/login` and `/auth/refresh`; the shared frontend
(`AuthContext.tsx`) stored it in `localStorage` — same XSS token-theft
exposure as dotnet's identical finding, and (since `frontend/omyfish-web`
is shared verbatim across java/dotnet/python-web) literally the same
vulnerable code dotnet already fixed in its own copy.

**Fix — httpOnly cookie**, same shape as dotnet's and python-web's:
`AuthController` now sets a `refresh_token` cookie (`HttpOnly`,
`SameSite=Strict`, `Path=/api/v1/auth`, 30-day max-age matching
`jwt.refresh-expiration-ms`'s default) on login/refresh instead of
returning it in the body; `/auth/refresh` now reads
`@CookieValue("refresh_token")` instead of a request-body field. Added
`POST /api/v1/auth/logout`. `AuthResponse` no longer has a `refreshToken`
field. Frontend (`AuthContext.tsx`, `api.ts`) updated to the same pattern
dotnet already shipped: `credentials: "include"` on login/refresh/logout,
no more `omyfish_refresh` in `localStorage`.

**Required companion change**: the gateway's
`spring.cloud.gateway.globalcors` config had `allowCredentials: false` —
flipped to `true` (safe since `allowedOrigins` is an explicit list, never a
wildcard, so no CORS-spec conflict).

Verified via `AuthControllerTest` (`@WebMvcTest`, no real Spring context) —
now 10 tests (was 7), covering cookie attributes, rotation on refresh,
missing-cookie → 401, and logout clearing the cookie. Full `mvn test`
(60 tests across 4 modules) and a `next build` of the shared frontend both
pass.

### 1.4 Containers run as root

**Problem:** none of the five Dockerfiles had a `USER` directive
(confirmed by grep across all five); no `securityContext`/`runAsNonRoot` in
the Helm chart's `deployment.yaml` (the only K8s manifests in this repo).

**Fix:** `RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app` +
`USER app` in all five service Dockerfiles (api-gateway, identity-service,
species-service, observation-service, notification-service) — pinned
uid/gid 1001 so Kubernetes can reference it explicitly, matching the
`frontend/omyfish-web/Dockerfile`'s existing `nextjs` user (also uid 1001).
Helm `deployment.yaml` gained a pod-level `securityContext`
(`runAsNonRoot: true`, `runAsUser/runAsGroup: 1001`) and a container-level
`allowPrivilegeEscalation: false` — applied to every service in the shared
`{{- range }}` loop **except `ai-service`**, which is built from the
separate `../omyfish-ai` repo and has no non-root user in its own Dockerfile
today; forcing `runAsUser` on its pod without first fixing that image would
just break it (a real bug caught by actually checking `../omyfish-ai`'s
Dockerfile before assuming the blanket template edit was safe — it isn't
part of this pass's scope). Verified via `helm lint` (clean) and `helm
template --show-only templates/deployment.yaml` (correct rendering for both
the included and excluded cases).

---

## 2. Resilience

**Status: not started in this pass** — see BACKLOG.md item G for what's
open and why it was deferred (larger/riskier changes than the security
tier; §2.3 in particular needs a live Postgres/RabbitMQ to verify against).

### 2.1 No timeout/retry/circuit-breaker on the AI `WebClient`

**Problem:** `AIServiceAdapter` builds its `WebClient` with no
`ClientHttpConnector` timeout and no `.timeout(Duration...)` on any of its
seven `.block()` call sites; no resilience4j dependency exists. Same class
of bug as dotnet's 2.1. **Not fixed in this pass.**

### 2.2 No global exception handling

**Problem:** zero `@ControllerAdvice`/`@RestControllerAdvice` classes exist
anywhere; only two controllers have local `@ExceptionHandler` methods.
Everything else (all of identity-service and notification-service)
produces Spring Boot's default unstructured error response on any
unhandled exception. **Not fixed in this pass.**

### 2.3 Dual-write without an outbox

**Problem:** `ObservationService.create()` saves the aggregate, then
separately (no `@Transactional`, no outbox) publishes
`ObservationCreatedEvent` via `RabbitMQEventPublisher`. Same crash-between-
steps risk as dotnet's identical finding — and dotnet's own fix for this
(MassTransit's EF Core outbox) took a dedicated round to implement and
verify live. **Not fixed in this pass** — the largest single item still
open; needs its own dedicated session per the same reasoning dotnet used.

### 2.4 No consumer idempotency

**Problem:** `ObservationCreatedConsumer.handle()` inserts a `Notification`
row on every delivery with no dedup key; the entity has no source-event-id
column, no unique constraint. A RabbitMQ redelivery creates a duplicate.
**Not fixed in this pass** — flagged as small-medium, good candidate for
the next round (needs a new Flyway migration + a dedup check, mirroring
dotnet's `Notification.SourceEventId` fix).

---

## 3. Data layer

**Status: 3.1 already fine, 3.2 not applicable, 3.3/3.4 not fixed.**

### 3.1 ORM auto-schema masking missed migrations — already fine

**Not applicable — already correct.** All three relational services use
Flyway with per-service journal tables (avoiding the cross-service
collision dotnet's own memory notes hit) and Spring Boot fails startup if a
migration doesn't apply — the same fail-fast guarantee DbUp was introduced
to provide in dotnet. No `ddl-auto: create`/`update` anywhere.
species-service uses MongoDB (schemaless), so this category doesn't apply
there at all.

### 3.2 "Run all migrations" target silently incomplete

**Not applicable.** Each service's Flyway runs its own `db/migration/`
folder automatically at startup — no dotnet-style single external runner
with a hand-maintained file list to drift.

### 3.3 Dead PostGIS geometry column/index

**Problem:** `observation-service`'s migration defines a `location
GEOMETRY(Point, 4326)` column and a GIST index, but `ObservationJpaEntity`
has no `location`/`Geometry` field — only plain `latitude`/`longitude`.
Unlike dotnet (which at least had a dead radius-search SQL function to
activate), no such function exists here either — pure dead weight with
nothing to wire up to. **Not fixed in this pass.**

### 3.4 N+1 query

**Problem:** `IdentificationService.identify()` does one MongoDB
round-trip per AI prediction (`findByScientificName`) instead of a single
batched lookup; `SpeciesRepository`'s port only exposes the singular form.
**Not fixed in this pass.**

---

## 4. Testing/CI

**Status: not started in this pass.**

- api-gateway has zero tests (`find services/api-gateway -path "*/test/*"`
  returns nothing) — including zero coverage of the exact "public route
  accidentally lets an authenticated route through" class of bug §1.1 was
  about, and now zero coverage of the new `RateLimitFilter` either.
- No `Testcontainers`/`@DataJpaTest` anywhere — nothing exercises a real
  JPA mapping against a real Postgres instance, so a schema/entity mismatch
  like §3.3's dead geometry column would stay invisible to the test suite
  indefinitely.
- `.gitlab-ci.yml` has an `integration-test` stage that spins up real
  `postgres`/`rabbitmq` containers and runs a Maven profile
  (`-Pintegration-tests`) that **isn't defined anywhere in `pom.xml`**, and
  there are zero `*IT.java` files in the repo — the job runs, starts two
  real containers, and tests nothing. Worse than an honest "no CI coverage"
  gap since it reads as covered in the pipeline config.
- `.github/workflows/ci.yml` only runs `mvn test` — no formatting check, no
  frontend build/lint, no image build, no dependency scan.

**Not fixed in this pass.**

---

## Cleanup

No dead-code/scaffold findings matching the dotnet items — the Helm chart
has real, non-placeholder templates, and `LoggingAspect` in species-service
is genuinely wired (AOP starter present, confirmed active).

---

## Bonus findings (not on the dotnet list, found during this pass)

1. **`.gitlab-ci.yml`'s Docker build stage silently omits two of five
   services** — only species-service, observation-service, and api-gateway
   have a `docker-*` job; identity-service and notification-service have
   none, so a change to either ships nowhere unless someone notices. Same
   "incomplete list nobody audits" shape as dotnet's §3.2, relocated to the
   Docker stage instead of migrations.
2. **AI-discovered species are never persisted in species-service** —
   `speciesRepository.save(...)` is called exactly once in the whole
   service (the startup seeder). `IdentificationService.identify()` builds
   a fresh in-memory `Species` for any unrecognized AI prediction, uses it
   for the response/event, then discards it — every future identical
   identification repeats the same construct-and-discard path. Same shape
   as the bug found and fixed in `omyfish-dotnet`'s own
   `IdentifyFishCommandHandler` while wiring its outbox (§2.3) — worth
   fixing alongside java's own §2.3 pass, since it's the same method.

Neither bonus finding is fixed in this pass.
