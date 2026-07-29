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
