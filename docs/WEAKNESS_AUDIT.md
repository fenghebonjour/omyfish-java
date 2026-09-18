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

**Status: §2.1, §2.2, §2.4 fixed 2026-09-11. §2.3 fixed 2026-09-18.**

### 2.1 No timeout/retry/circuit-breaker on the AI `WebClient`

**Problem:** `AIServiceAdapter` built its `WebClient` with no
`ClientHttpConnector` timeout and no `.timeout(Duration...)` on any of its
seven `.block()` call sites. Same class of bug as dotnet's 2.1.

**Fix:** a `ReactorClientHttpConnector` wrapping a Reactor Netty `HttpClient`
with a 10s connect timeout and a 15s response timeout, applied once at
construction — covers all seven `.block()` sites uniformly. No
resilience4j/circuit-breaker added (matches dotnet's own decision to ship
the timeout alone first) — a slow `.block()` now throws instead of hanging,
but still surfaces as a generic 500 until §2.2's handler is in front of it
(now true, since §2.2 landed in the same pass).

### 2.2 No global exception handling

**Problem:** zero `@ControllerAdvice`/`@RestControllerAdvice` classes
existed; only two controllers had local `@ExceptionHandler` methods.
Everything else produced Spring Boot's default unstructured error response
on any unhandled exception.

**Fix:** a `GlobalExceptionHandler` (`@RestControllerAdvice`) added to each
of the four services with REST controllers (identity, species, observation,
notification) — **extends `ResponseEntityExceptionHandler`**, not a bare
`@ExceptionHandler(Exception.class)`. That distinction mattered in
practice: a first attempt with the bare form broke a real test
(`ObservationControllerTest#createObservation_missingUserIdHeader_returns400`
started getting 500 instead of 400) because it intercepted Spring's own
framework-level exceptions (missing required header, unreadable body,
failed `@Valid` binding) before Spring's built-in resolution could turn
them into the correct 4xx — extending the base class puts those inherited,
more-specific handlers back in the resolution set ahead of the generic
catch-all. `ResponseStatusException` (used throughout identity-service for
401/404/409) gets its own explicit handler for the same reason. Verified:
full `mvn test` green across all 4 services after this fix, plus the
regression it originally caused.

### 2.3 Dual-write without an outbox

**Status: fixed 2026-09-18.**

**Problem:** `ObservationService.create()` saved the aggregate, then
separately (no `@Transactional`, no outbox) published
`ObservationCreatedEvent` via `RabbitMQEventPublisher`. Same crash-between-
steps risk as dotnet's identical finding — a process crash (or network
failure) between the save and the publish call would leave the observation
persisted with no corresponding notification ever generated. Java has no
MassTransit-style built-in outbox, so this needed a hand-built polling
publisher rather than a framework feature.

**Fix — transactional outbox + polling publisher:**
- `V2__create_outbox_events_table.sql` adds `observation.outbox_events`
  (id, event_type, exchange, routing_key, payload, created_at,
  published_at nullable) with a partial index on unpublished rows.
- `EventPublisherPort`'s Spring implementation changed from
  `RabbitMQEventPublisher` (deleted — it published to RabbitMQ directly) to
  `OutboxEventPublisher` (`adapter/out/persistence`), which just serializes
  the domain event to JSON and inserts an outbox row — no broker call at
  write time.
- `OutboxPublisherJob` (`adapter/out/messaging`, `@Scheduled(fixedDelay =
  5000)`) polls unpublished rows, sends each to RabbitMQ, then marks it
  published — all inside one `@Transactional` method, so a crash between
  the send and the commit just means the row gets resent next poll
  (acceptable at-least-once — `ObservationCreatedConsumer` already dedups
  by event id per §2.4, so a resend is a no-op on the consumer side).
- The atomicity the whole pattern depends on — the observation insert and
  the outbox insert committing or rolling back together — needed a
  transaction boundary somewhere, and neither `domain/` nor `application/`
  may import Spring annotations per this repo's hexagonal rule. Solution:
  `TransactionalCreateObservationUseCase` (`config/`), a thin
  `@Transactional`-annotated decorator around `ObservationService.create()`,
  registered as the `@Primary` `CreateObservationUseCase` bean in
  `AppConfig` ahead of the plain (non-transactional) `ObservationService`
  bean that also implements the same interface. `ObservationService` itself
  is untouched — no Spring in `application/`.
- Single-instance assumption, same as the §1.2 rate limiter's: no claim/lock
  step on the poll, so a second replica would double-publish every row
  (harmless given consumer idempotency, but wasteful) — revisit if
  observation-service is ever scaled horizontally.

**Verified:**
- `OutboxEventPublisherTest`, `OutboxPublisherJobTest` — plain Mockito unit
  tests (outbox row shape, Rabbit send + mark-published behavior).
- `ObservationOutboxIntegrationTest` — `@SpringBootTest` + Testcontainers
  (real Postgres + real RabbitMQ): confirms `create()` leaves an unpublished
  outbox row, and that running the poller actually delivers the message to
  a bound test queue and marks the row published.
- `ObservationOutboxAtomicityTest` — the actual guarantee the pattern is
  for: a `@Primary` test `EventPublisherPort` forces a failure inside the
  transaction, and asserts the observation row was rolled back too (not
  left orphaned with no event).
- All three new test classes are written and compile clean, but **could not
  actually be executed in this session** — this WSL environment's Docker
  Desktop responds to plain `docker`/`curl` calls against
  `/var/run/docker.sock` correctly, but testcontainers' own HTTP client gets
  back a stubbed, mostly-empty `/info` response with an HTTP 400, so
  `DockerClientProviderStrategy` can't find a valid environment — confirmed
  this isn't a testcontainers version issue (same failure on 1.19.8, 1.21.3)
  and confirmed it's not new to this change (the pre-existing §3.3
  Testcontainers test fails the exact same way here). Environment-level
  Docker Desktop/WSL2 issue, not a code problem. Run
  `mvn test -pl services/observation-service -Dtest=ObservationOutbox*Test`
  once Testcontainers can actually reach a Docker daemon from this machine.
  All non-Testcontainers tests in the module (20 tests) pass.

### 2.4 No consumer idempotency

**Problem:** `ObservationCreatedConsumer.handle()` inserted a `Notification`
row on every delivery with no dedup key; the entity had no source-event-id
column, no unique constraint. A RabbitMQ redelivery creates a duplicate.

**Fix:** matches dotnet's `Notification.SourceEventId` approach —
`V2__add_source_event_id.sql` adds a nullable `source_event_id` column
with a partial unique index; `Notification` gained a `sourceEventId` field
(set from the integration event's own `eventId()`, which every publisher
already generates); the consumer now checks
`repository.existsBySourceEventId(event.eventId())` before inserting and
skips (logs + returns) on a redelivery instead of inserting a duplicate.
Verified via a new `ObservationCreatedConsumerTest` case.

---

## 3. Data layer

**Status: 3.1 already fine, 3.2 not applicable, 3.4 fixed 2026-09-11, 3.3
fixed 2026-09-17.**

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

**Status: fixed 2026-09-17.**

**Problem:** `observation-service`'s migration defines a `location
GEOMETRY(Point, 4326)` column and a GIST index, but `ObservationJpaEntity`
has no `location`/`Geometry` field — only plain `latitude`/`longitude`.
Unlike dotnet (which at least had a dead radius-search SQL function to
activate), no such function exists here either — pure dead weight with
nothing to wire up to.

**Fix (user decision: wire it up, don't drop):** `ObservationJpaEntity`
gained a `Point location` field (JTS, via the already-managed
`hibernate-spatial` dependency), populated from `GpsCoordinates` alongside
the existing `latitude`/`longitude` columns in `from()` — lat/lng stay the
read-side source of truth (`toDomain()` unchanged), `location` exists purely
for the GIST index to serve spatial queries. Added
`ObservationRepository.findWithinRadius(lat, lng, radiusMeters)` (port) →
`ObservationJpaRepository.findWithinRadius` (native `ST_DWithin` query
against `location::geography`) → `ObservationRepositoryAdapter`. No new
public endpoint added: the frontend that would call a "nearby observations"
feature lives in a separate repo (extracted per the recent frontend-repos
change) with no such feature request today, so this stays a repository-level
capability per CLAUDE.md's Simplicity First guideline — a controller/use-case
can wire it up when an actual caller needs it.

**Verified:** `ObservationRepositoryAdapterRadiusSearchTest`
(`@DataJpaTest` + Testcontainers, `postgis/postgis:16-3.4-alpine` — the same
image `docker-compose.yml` uses) asserts `ST_DWithin` returns a point 0km
away and excludes one ~500km away. Compiles clean and the rest of the
module's unit tests (14) stay green, but **the new test itself could not be
executed in this session** — this WSL environment has no working Docker
daemon (`docker: command not found` / daemon unreachable), which
Testcontainers requires. Run it yourself once Docker is available:
`mvn test -pl services/observation-service -Dtest=ObservationRepositoryAdapterRadiusSearchTest`.

### 3.4 N+1 query

**Problem:** `IdentificationService.identify()` did one MongoDB round-trip
per AI prediction (`findByScientificName`) instead of a single batched
lookup; `SpeciesRepository`'s port only exposed the singular form.

**Fix:** `SpeciesRepository.findByScientificNames(Collection<String>)`
(backed by Spring Data's derived `findByScientificNameIn` on the Mongo
repository) — `identify()` now collects every prediction's scientific name
upfront, does one batched lookup, and builds an in-memory
`Map<String, Species>` for the per-prediction loop to consult. Verified via
`IdentificationServiceTest` (updated to stub the batched method — Mockito's
strict-stubs mode caught every now-unused `findByScientificName` stub as an
error, which is how the test updates were driven).

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
   services — fixed 2026-09-17.** Only species-service,
   observation-service, and api-gateway had a `docker-*` job; identity-service
   and notification-service had none, so a change to either shipped nowhere
   unless someone noticed. Same "incomplete list nobody audits" shape as
   dotnet's §3.2, relocated to the Docker stage instead of migrations.

   **Fix:** `docker-identity-service` and `docker-notification-service` jobs
   added, mirroring the existing three via the same `.docker-build`
   template. Verified the YAML parses (`python3 -c "import yaml; ..."`) and
   diffed against the existing jobs for the template/path convention — no
   live GitLab pipeline available in this environment to run it end-to-end.

   **Noticed but out of scope:** `deploy-staging`/`deploy-production` only
   pass `--set image.speciesService.tag=$IMAGE_TAG` /
   `image.observationService.tag` / `image.apiGateway.tag` to Helm —
   identity-service and notification-service's Helm values keep whatever
   tag is baked into `values.yaml` instead of the CI-built `$IMAGE_TAG`,
   even now that their images get built and pushed. Same class of gap, one
   stage over; not fixed here since it wasn't part of this finding.
2. **AI-discovered species are never persisted in species-service — fixed
   2026-09-17.** `IdentificationService.identify()` built a fresh in-memory
   `Species` for any unrecognized AI prediction, used it for the
   response/event, then discarded it — every future identical
   identification repeated the same construct-and-discard path. Same shape
   as the bug found and fixed in `omyfish-dotnet`'s own
   `IdentifyFishCommandHandler` while wiring its outbox (§2.3).

   **Fix:** the fallback branch now calls `speciesRepository.save(...)` on
   the constructed `Species` before using it, so the next identical
   identification hits the batched MongoDB lookup (§3.4) instead of
   reconstructing it. Verified via `IdentificationServiceTest` (10 tests, up
   from 9 — existing fallback-branch tests updated to stub `save()`, plus a
   new case asserting it's called with the right species) and a full
   `mvn test -pl services/species-service` (18 tests, green).

Bonus finding 1 (`.gitlab-ci.yml` docker-build stage) is not fixed in this
pass.
