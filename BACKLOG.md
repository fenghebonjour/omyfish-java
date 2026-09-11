# OMyFish Java — Backlog

Deferred ideas and future work. Not committed scope — parking lot for things worth doing.

Cross-repo context lives in the family alignment plan
(`/home/bigblue/.claude/plans/wondrous-shimmying-ripple.md`) — this file tracks
just Java's slice of it.

---

## [x] A1 — Route versioning: move auth/billing/admin under /api/v1

**Status:** DONE (2026-07-28, commit c18c0a2). Gateway predicates, each
controller's `@RequestMapping`, `AuthFilter`'s public-path whitelist,
`SecurityConfig`, the frontend's `api.ts`, and `ARCHITECTURE.md`'s stale
auth-contract docs all updated. 23 identity-service tests + api-gateway
compile verified green.

---

## [x] A3 — Port features .NET already has

**Status:** DONE (2026-07-28, commit 42ec789).

- GPS-directory parsing added to `ExifExtractorAdapter.java` (still unwired
  into observation-create in both stacks, as planned).
- `displayName` + `isActive`/`deactivate()` added to `User.java` +
  `RegisterRequest` + `RegisterUseCase`; Flyway migration V3 adds the columns.
- Ported Grafana provisioning, rewritten for Micrometer/Actuator metric names
  (not ASP.NET's); enabled percentile-histogram export per service so
  P95/P99 panels have data; swapped .NET's in-flight-requests panel for JVM
  live threads (no stock equivalent gauge in Spring Boot).
- `FishIdentifiedConsumer` stub (log + TODO) added to notification-service,
  with matching exchange/queue/binding beans.

---

## [x] A4 — Stale docs

**Status:** DONE (2026-07-28, commit b8f5a46). Fixed the 168->336 default and
the notification-service description + RabbitMQ queue-name diagram in
ARCHITECTURE.md.

---

## [x] B — Proxy the Quebec Regs Advisor feature

**Status:** DONE (2026-07-28, commit b00686f). Implemented at
`/api/v1/species/regs/*` — **corrected from this file's original
`/api/v1/regs/*`**: species-service's gateway route only catches
`/api/v1/species/**`, and .NET's own bite-score proxy is already nested the
same way (`/api/v1/species/bite-score/...`), so nesting under `/species` avoids
any gateway config change and matches the existing sibling convention. Same
correction applies to the dotnet and python-web BACKLOG entries below.

---

## [x] C — Frontend unification (adopt .NET's architecture as baseline)

**Status:** DONE (2026-07-29, commit d200d9a). `frontend/omyfish-web` replaced
wholesale with the finalized `omyfish-dotnet` baseline — already at the locked
contract, now with the Regs Advisor UI (chat page, identify info cards,
toggle-able zones/stations map overlay) and a Next.js security bump
(15.1.0 → 15.5.22, fixes a critical RCE + ~15 other CVEs). Verified
byte-identical to `omyfish-dotnet`'s and `omyfish-python-web`'s copies
(`diff -rq`, excluding node_modules/.next) and with a clean `next build` in
this repo.

**All workstreams for this repo are now complete.**

---

## [ ] D — Spec-driven contract tests for cross-service boundaries

**Status:** IN PROGRESS (2026-08-01). Piloted on `FishIdentifiedEvent`:
`shared/omyfish-shared-events/asyncapi/fish-identified.yaml` (AsyncAPI schema,
source of truth for the event's wire shape) +
`services/species-service/src/test/java/com/omyfish/species/contract/FishIdentifiedEventContractTest.java`
(validates the publisher's actual serialized payload against that schema via
`com.networknt:json-schema-validator`). Verified the test catches drift, not
just passes trivially — mutation-tested by tightening a schema bound and
confirming the test fails, then reverting.

**Scoping decision from this session:** apply this only where two
independently-tested Spring services must agree on something neither one's
own test suite can verify — i.e. cross-service event contracts and the
gateway route whitelist. Do **not** extend it to REST APIs like
species-service/observation-service CRUD, since those currently have exactly
one consumer (the Next.js frontend, same repo, edited in the same PR) —
spec+contract-test overhead there would be enforcing agreement between two
things already changed together, no real drift risk yet.

**Remaining scope:**
- [ ] `ObservationCreatedEvent` — same treatment (AsyncAPI schema + producer
  contract test in observation-service).
- [ ] Consumer-side contract tests for `FishIdentifiedEvent` in
  observation-service and notification-service (current test only guards the
  publisher, not either consumer).
- [ ] Gateway route whitelist vs. actual controller `@RequestMapping`s —
  needs its own spec artifact (likely a small OpenAPI-path-list check rather
  than full AsyncAPI) so `AuthFilter.PUBLIC_PREFIXES` can't silently drift
  from what's actually exposed.
- [ ] Revisit if a second REST API consumer ever appears (public API, a
  third service) — that's the trigger to add OpenAPI contract tests for the
  REST surface too.

---

## [x] E — Migrate species catalog persistence to MongoDB

**Status:** DONE (2026-08-19, commit 36c0200). Species catalog is read-mostly,
flexible-schema reference data with no relational integrity needs, so it moved
off PostgreSQL/JPA/Flyway onto its own MongoDB instance behind the existing
`SpeciesRepository` domain port. `SpeciesJpaEntity`/`SpeciesJpaRepository`/
`V1__create_species_table.sql` replaced with `SpeciesDocument`/
`SpeciesMongoRepository`; `docker-compose.yml` gained a `mongodb` service;
`Makefile`'s `migrate` target no longer touches species-service. Also fixed a
pre-existing bug while touching this code: `toDomain()` was generating a fresh
random id instead of restoring the persisted one — added a
`Species.reconstitute()` factory mirroring the pattern already used in
`observation-service`'s `Observation`. Full `mvn test` suite green, verified
end to end via `docker compose up -d --build`.

**Not yet done:** the same move for `omyfish-dotnet` (`SpeciesDbContext` /
EF Core+Npgsql) and `omyfish-python-web` (`apps/species` Django ORM+Postgres
model) — tracked as their own backlog item E in each of those repos'
`BACKLOG.md`. User deferred to a later session (2026-08-19); do each as its
own dedicated pass, not folded into unrelated work, since the ORM/migration
mechanics differ per stack.

---

## [x] F — Regs & Tips: render chat answers as Markdown, not raw text

**Status:** DONE (2026-08-25, commit e510503, bundled with the Angular
frontend twin). `/regs/ask` returns Groq-generated Markdown (bold, bullet
lists, etc.), but the shared frontend's chat UI dumped it into plain text,
so users saw literal `**`/`-` characters. Same bug independently found and
fixed the same day in `omyfish-python-web` (commit 88503de) and
`omyfish-dotnet` (commit 4e7e38b) via `react-markdown` — expected, since all
three share `frontend/omyfish-web` byte-for-byte (item C above).
`omyfish-ios` has its own separate SwiftUI chat view and carried the same
bug until 2026-08-28 (commit e53b418), fixed there via
`AttributedString(markdown:)`.

---

## [~] G — Weakness audit follow-up (ported from omyfish-dotnet)

**Status:** IN PROGRESS (added 2026-09-11). `omyfish-dotnet` went through a
senior-dev-style weakness audit (its `BACKLOG.md` item F) covering security,
resilience, data-layer, and testing/CI findings, then asked for the same
treatment across the other enterprise siblings. Full explanation in
`docs/WEAKNESS_AUDIT.md` — this file is the "what shipped". This repo shares
dotnet's microservices shape, so most findings translate directly. Security
tier done 2026-09-11; most of the resilience tier done the same day
(§2.1/§2.2/§2.4) — §2.3 (the outbox pattern), data layer, and testing/CI
remain, same as dotnet's own pacing across sessions.

**Security — DONE 2026-09-11:**
- ~~No rate limiting on `/api/v1/species/**`~~ — fixed: a small in-memory
  per-IP fixed-window `RateLimitFilter` (not Redis-backed — no Redis exists
  in this stack, and api-gateway is a single instance here), same rates as
  dotnet (`identify`: 10/min, `bite-score`: 30/min). (`WEAKNESS_AUDIT.md` §1.2)
- ~~Refresh token in response body + `localStorage`~~ — fixed: `AuthController`
  now sets an httpOnly, `SameSite=Strict` cookie scoped to `/api/v1/auth`
  instead of returning it in `AuthResponse`; added `POST /api/v1/auth/logout`.
  Flipped the gateway's CORS `allowCredentials` to `true` (safe — its
  `allowedOrigins` is an explicit list, never a wildcard). Frontend
  (`AuthContext.tsx`/`api.ts`, shared with dotnet/python-web) updated to the
  same already-shipped dotnet pattern. Verified via `AuthControllerTest`
  (10 tests, up from 7) plus a full `mvn test`/`next build` pass. (§1.3)
- ~~Containers run as root~~ — fixed: `USER app` (pinned uid/gid 1001) in
  all five service Dockerfiles; Helm `deployment.yaml` gained a pod-level
  `securityContext`/container `allowPrivilegeEscalation: false` — applied
  to every service **except `ai-service`**, whose Dockerfile (in the
  separate `../omyfish-ai` repo) has no non-root user today; forcing
  `runAsUser` there without fixing that image first would have broken its
  pod (confirmed by actually checking, not assumed). Verified via `helm
  lint` and `helm template` rendering for both cases. (§1.4)
- §1.1 (gateway configures auth but doesn't enforce it) — not applicable,
  already correct (`AuthFilter` is default-deny by construction).

**Resilience — mostly DONE 2026-09-11:**
- ~~AI `WebClient` has no timeout/retry/circuit-breaker~~ — fixed: a
  `ReactorClientHttpConnector` with a 10s connect / 15s response timeout,
  applied once at construction (covers all seven `.block()` call sites).
  No circuit-breaker added, matching dotnet's own decision to ship the
  timeout alone first. (§2.1)
- ~~No global exception handling~~ — fixed: a `GlobalExceptionHandler`
  (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) in
  each of the four services with REST controllers. Extending the base
  class (not a bare `@ExceptionHandler(Exception.class)`) mattered in
  practice — an initial bare version broke a real test by intercepting
  Spring's own framework-level exceptions (missing header → 400) before
  they could resolve correctly; caught by running the full suite before
  committing. `ResponseStatusException` gets its own handler so
  identity-service's existing 401/404/409s keep their status codes.
  Verified: full `mvn test` green across all 4 services. (§2.2)
- ~~No consumer idempotency~~ — fixed: `V2__add_source_event_id.sql` adds
  a nullable `source_event_id` column + partial unique index;
  `ObservationCreatedConsumer` now skips a redelivered event
  (`existsBySourceEventId`) instead of inserting a duplicate notification
  — same shape as dotnet's `Notification.SourceEventId` fix. Verified via
  a new `ObservationCreatedConsumerTest` case. (§2.4)
- §2.3 dual-write without an outbox in `ObservationService.create()` —
  **not done**, the largest item; dotnet's own fix for this took a
  dedicated round to implement and verify against a live Postgres/RabbitMQ,
  so this needs the same treatment rather than being folded into a broader
  pass.

**Data layer, Testing/CI — not started, left for follow-up rounds** (see
`WEAKNESS_AUDIT.md` for full detail on each):
- §3.3 dead PostGIS geometry column/index in observation-service (no
  radius-search function exists to activate, unlike dotnet's version).
- §3.4 N+1 species lookup in `IdentificationService.identify()`.
- Testing/CI: api-gateway has zero tests (now including zero coverage of
  the new `RateLimitFilter`); no Testcontainers/`@DataJpaTest` anywhere;
  `.gitlab-ci.yml`'s `integration-test` stage runs a Maven profile that
  doesn't exist and tests nothing despite looking covered.
- Bonus: `.gitlab-ci.yml`'s Docker build stage omits identity-service and
  notification-service images; AI-discovered species are never persisted
  in species-service (same shape as a bug found+fixed in dotnet's own §2.3
  pass — worth doing alongside java's §2.3 when that round happens).
