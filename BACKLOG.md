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

## [ ] C — Frontend unification (adopt .NET's architecture as baseline)

**Status:** NOT STARTED. Depends on A1 and B landing (contract + new endpoints
to build against).

`omyfish-dotnet`'s frontend independently evolved a cleaner pattern
(`AuthContext`, namespaced `api.*` client, dedicated `/register` page, generic
`ObservationMap`). Once that baseline is adjusted for Java's field names
(`token`/`refreshToken`/uppercase role, kept per the family decision) and
includes the Regs Advisor UI (chat panel, identify-result info cards, map
zone/station layer), copy it into `frontend/omyfish-web` here, replacing the
current frontend wholesale (same process used to produce
`omyfish-python-web`'s copy).
