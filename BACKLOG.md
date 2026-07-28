# OMyFish Java — Backlog

Deferred ideas and future work. Not committed scope — parking lot for things worth doing.

Cross-repo context lives in the family alignment plan
(`/home/bigblue/.claude/plans/wondrous-shimmying-ripple.md`) — this file tracks
just Java's slice of it.

---

## [ ] A1 — Route versioning: move auth/billing/admin under /api/v1

**Status:** NOT STARTED.

`api-gateway`'s `application.yml` and each controller's `@RequestMapping` route
`/api/auth/**`, `/api/users/**`, `/api/billing/**`, `/api/admin/**` unversioned,
while species/observation/notification already use `/api/v1/...`. Family-wide
decision: adopt .NET's uniform `/api/v1/...` everywhere. Blocks the frontend
unification work (Workstream C) and the Regs Advisor proxy routes (below) —
do this first.

---

## [ ] A3 — Port features .NET already has

**Status:** NOT STARTED.

- Add GPS-directory parsing to `ExifExtractorAdapter.java` (reads camera/date/
  dimensions today, no GPS; .NET's `ExifExtractor.cs` already does this). Leave
  wiring into observation-create as future work — neither stack wires it in yet.
- Add `displayName` and `isActive`/deactivation to `User.java` + `RegisterRequest`
  (Java's own ARCHITECTURE.md already documents `displayName` on register; it
  was never implemented).
- Port `.NET`'s `infrastructure/grafana/provisioning/` (dashboard + datasource
  JSON) so Grafana isn't empty on first `docker compose up`.
- Add a `FishIdentifiedEvent` queue/consumer to notification-service (.NET has
  a stub `FishIdentifiedConsumer`; Java has none — bring Java to the same
  stub-level parity, TODO comment and all).

---

## [ ] A4 — Stale docs

**Status:** NOT STARTED.

- `BiteScoreController.forecast`'s default `hours=168` contradicts its own
  ARCHITECTURE.md (documents 336) and .NET's actual behavior (336). Fix the
  code to 336.
- notification-service's ARCHITECTURE.md claims "no REST API — pure event
  consumer" but it has one (`NotificationController.java`). Fix the doc.

---

## [ ] B — Proxy the Quebec Regs Advisor feature

**Status:** NOT STARTED. Depends on A1 (route versioning) landing first.

All chatbot/retrieval logic lives in `omyfish-ai` (frozen) at `/regs/*`
(`GET /limits`, `GET /zones/geojson`, `GET /consumption/stations`,
`GET /consumption`, `POST /ask`). Add a thin proxy in species-service —
`RegsController` under `/api/v1/regs/*` — following the existing
`AIServicePort`/`AIServiceAdapter` hexagonal pattern already used for
bite-score. Register the routes in the gateway.

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
