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

## [x] D — Spec-driven contract tests for cross-service boundaries

**Status:** DONE (piloted 2026-08-01, remaining scope completed 2026-09-18).
Piloted on `FishIdentifiedEvent`:
`shared/omyfish-shared-events/asyncapi/fish-identified.yaml` (AsyncAPI schema,
source of truth for the event's wire shape) +
`services/species-service/src/test/java/com/omyfish/species/contract/FishIdentifiedEventContractTest.java`
(validates the publisher's actual serialized payload against that schema via
`com.networknt:json-schema-validator`). Verified the test catches drift, not
just passes trivially — mutation-tested by tightening a schema bound and
confirming the test fails, then reverting.

**Scoping decision from the pilot session:** apply this only where two
independently-tested Spring services must agree on something neither one's
own test suite can verify — i.e. cross-service event contracts and the
gateway route whitelist. Do **not** extend it to REST APIs like
species-service/observation-service CRUD, since those currently have exactly
one consumer (the Next.js frontend, same repo, edited in the same PR) —
spec+contract-test overhead there would be enforcing agreement between two
things already changed together, no real drift risk yet.

**Remaining scope — all done 2026-09-18:**
- ~~`ObservationCreatedEvent` — same treatment~~ — fixed:
  `shared/omyfish-shared-events/asyncapi/observation-created.yaml` +
  `ObservationCreatedEventContractTest` in observation-service, exercising
  the real production path (`OutboxEventPublisher.publish()`, captured via
  Mockito) rather than a hand-rolled parallel serialization. Surfaced a real
  finding in the process: the outbox payload includes an `eventType` field
  the shared consumer-facing record doesn't have, because the outbox
  serializes observation-service's own domain event class directly — the
  schema now documents that field as part of the actual wire shape rather
  than pretending it doesn't exist. Mutation-tested (tightened a bound,
  confirmed failure, reverted — the file wasn't yet tracked in git, so the
  revert had to be done by hand rather than via `git checkout`).
- ~~Consumer-side contract tests for `FishIdentifiedEvent`~~ — fixed:
  `FishIdentifiedEventConsumerContractTest` in both observation-service and
  notification-service. Each builds the message exactly as species-service's
  own `Jackson2JsonMessageConverter` would send it, then feeds it through
  *that service's own* configured converter (observation-service: default
  `TYPE_ID` precedence; notification-service: `INFERRED`) plus a real
  `MethodParameter` for that service's `FishIdentifiedConsumer.handle(...)`,
  asserting record-equality after the round trip and that the consumer
  doesn't throw. (`TYPE_ID` happens to also work for this one event today,
  since all three services share the exact same `FishIdentifiedEvent`
  class — unlike `ObservationCreatedEvent`, which is why notification-service
  needed `INFERRED` in the first place per §2.3/§2.4's history.)
- ~~Gateway route whitelist vs. actual controller `@RequestMapping`s~~ —
  fixed: `shared/api-contracts/public-routes.yaml` (new spec artifact, a
  small custom prefix+service+reason list, not full OpenAPI) is what both
  sides validate against. `AuthFilter.PUBLIC_PREFIXES` changed from
  `private` to package-private so api-gateway's `PublicRoutesContractTest`
  can assert it matches the spec exactly. identity-service, species-service,
  and observation-service each got a `GatewayPublicRoutesContractTest` that
  reflects over their actual controllers' merged `@RequestMapping` values
  (via `AnnotatedElementUtils` — resolves `@GetMapping`/`@PostMapping`/etc.
  uniformly, no Spring context needed) and asserts the subset matching a
  public prefix equals a hardcoded, reviewed expected set — a new endpoint
  landing under an already-public prefix changes that actual subset and
  fails the test until someone deliberately updates the expected set, which
  is the actual protection this is for. All four contract tests
  mutation-tested (broke one on purpose, confirmed the failure, reverted).
- Revisit-if-second-REST-consumer-appears trigger from the pilot: still not
  triggered, no change.

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

## [x] G — Weakness audit follow-up (ported from omyfish-dotnet)

**Status:** DONE (added 2026-09-11, completed 2026-09-18). `omyfish-dotnet`
went through a senior-dev-style weakness audit (its `BACKLOG.md` item F)
covering security, resilience, data-layer, and testing/CI findings, then
asked for the same treatment across the other enterprise siblings. Full
explanation in `docs/WEAKNESS_AUDIT.md` — this file is the "what shipped".
This repo shares dotnet's microservices shape, so most findings translate
directly. Security tier done 2026-09-11; most of the resilience tier done
the same day (§2.1/§2.2/§2.4); §3.4 (N+1) done the same day too; §3.3 (dead
PostGIS column), the AI-species-persistence bonus, and the gitlab-ci
docker-build bonus done 2026-09-17; §2.3 (the outbox pattern) and §4
(Testing/CI) done 2026-09-18 — every item now fixed or explicitly marked
not-applicable, same pacing dotnet used across its own sessions.

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
- ~~§2.3 dual-write without an outbox in `ObservationService.create()`~~ —
  **fixed 2026-09-18**: `OutboxEventPublisher` writes the event to a new
  `outbox_events` table in the same transaction as the observation save
  (via a `@Transactional` decorator in `config/`, since `application/` must
  stay Spring-free); `OutboxPublisherJob` polls it separately and does the
  actual RabbitMQ send, marking rows published — at-least-once, backed by
  §2.4's existing consumer dedup. Verified via unit tests plus two
  Testcontainers integration tests (happy path + an atomicity test that
  forces the outbox write to fail and asserts the observation rolls back
  too) — **written but unexecuted**: this WSL session's Docker Desktop
  answers plain `docker`/`curl` calls fine but testcontainers' HTTP client
  gets a stubbed empty response, confirmed unrelated to testcontainers
  version and confirmed pre-existing (§3.3's Testcontainers test hits the
  same wall here). Run
  `mvn verify -Pintegration-tests -pl services/observation-service -am -Dit.test=ObservationOutbox*IT`
  once that's resolved. All other tests in the module (20) pass.

**Data layer:**
- ~~N+1 species lookup in `IdentificationService.identify()`~~ — fixed:
  `SpeciesRepository.findByScientificNames(Collection<String>)` (Spring
  Data's derived `findByScientificNameIn` on the Mongo side), one batched
  lookup instead of one per AI prediction. Verified via
  `IdentificationServiceTest`. (§3.4)
- ~~§3.3 dead PostGIS geometry column/index in observation-service~~ —
  **fixed 2026-09-17** (user decision: wire it up, not drop).
  `ObservationJpaEntity` now populates a JTS `Point location` field
  alongside lat/lng; added `ObservationRepository.findWithinRadius(...)`
  backed by a native `ST_DWithin` query. No new public endpoint — no caller
  needs it yet (frontend lives in a separate repo, no such feature
  requested), so it stops at the repository layer per Simplicity First.
  Verified via a new Testcontainers (`postgis/postgis`) test that compiles
  and the rest of the module's suite (14 tests) stays green, but **the new
  test itself is unexecuted** — no Docker daemon available in this session;
  run `mvn verify -Pintegration-tests -pl services/observation-service -am
  -Dit.test=ObservationRepositoryAdapterRadiusSearchIT` once Docker is up.
  (Now runs via the real `integration-tests` profile added below — this
  test was renamed `*Test` → `*IT` as part of that fix.)

**Testing/CI — fixed 2026-09-18:**
- ~~api-gateway has zero tests~~ — fixed: `AuthFilterTest` (public-path
  bypass, missing/malformed/refresh-typed-token rejection, valid-token
  header forwarding, prod-secret guard) + `RateLimitFilterTest`
  (unthrottled paths, the 10/min and 30/min limits actually tripping,
  per-IP isolation). 10 tests, plain unit tests, no Spring context needed.
- ~~`.gitlab-ci.yml`'s `integration-test` stage runs a Maven profile that
  doesn't exist~~ — fixed: root `pom.xml` gained a real `integration-tests`
  profile (Failsafe, bound to `integration-test`+`verify`); the three
  existing Testcontainers tests were renamed `*Test` → `*IT`
  (`ObservationOutboxIT`, `ObservationOutboxAtomicityIT`,
  `ObservationRepositoryAdapterRadiusSearchIT`), which moves them out of
  `mvn test`'s default surefire scope into Failsafe's automatically (no
  include/exclude config needed — that's the whole point of the naming
  convention). Had to explicitly set Failsafe's `classesDirectory` to plain
  `target/classes`, since its `integration-test` phase runs after
  `package`, where a Spring Boot module's main artifact is the repackaged
  executable jar (`BOOT-INF/classes` layout) that JUnit's classpath scanner
  can't see into — without that, discovery failed with a bare `TestEngine
  with ID 'junit-jupiter' failed to discover tests` and no other detail.
  `.gitlab-ci.yml`'s `integration-test` job also had its `postgres`/
  `rabbitmq` service sidecars (never actually reachable by Testcontainers,
  which spins up its own containers) replaced with Docker-in-Docker,
  matching the `.docker-build` jobs further down the same file —
  **unverified**, no live GitLab remote for this repo. `.github/
  workflows/ci.yml` gained a second `integration-test` job running `mvn
  verify -Pintegration-tests` on `ubuntu-latest`'s native Docker daemon —
  verified green after push.
- `mvn test` is now genuinely Docker-independent across the whole repo
  (confirmed via a full `mvn test` run) — previously the three
  Testcontainers tests silently made default `mvn test` Docker-dependent
  too, just not documented as such.
- ~~Bonus: `.gitlab-ci.yml`'s Docker build stage omits identity-service and
  notification-service images~~ — **fixed 2026-09-17**: added
  `docker-identity-service`/`docker-notification-service` jobs mirroring the
  existing three. Noticed but out of scope: `deploy-staging`/
  `deploy-production` still don't pass `$IMAGE_TAG` for these two services'
  Helm values, so their images build/push now but the deploy step doesn't
  pick up the new tag yet — same gap, one stage over.

**Bonus — AI-discovered species never persisted — fixed 2026-09-17:**
`IdentificationService.identify()`'s fallback branch now calls
`speciesRepository.save(...)` on the constructed `Species` instead of
discarding it, so repeat identifications of the same unrecognized fish hit
the catalog lookup instead of reconstructing it every time. Verified via
`IdentificationServiceTest` (10 tests) and a full `mvn test -pl
services/species-service` (18 tests, green).

**Remaining open items:** none — item G is complete. Noticed but out of
scope while closing it out: `make fmt`/`make lint` invoke `spotless:apply`/
`spotless:check checkstyle:check`, but neither plugin is configured in any
`pom.xml`, so both commands currently fail; not fixed here since choosing a
formatting/lint policy is a separate decision from this audit.

---

## [x] H — Billing/payments: 3DS checkout, admin refunds, saved payment methods

**Status:** DONE (2026-10-06, commits 7651f63, 0054adc, 09f7c50). Three Stripe
changes in `identity-service`'s billing slice, same session:

- ~~Checkout hidden SCA/3DS behind Stripe's hosted redirect~~ — fixed
  (7651f63): `/billing/checkout` no longer creates a Stripe Checkout Session;
  it creates a Subscription with `payment_behavior=default_incomplete` and
  returns `{clientSecret, subscriptionId, status}`, so 3DS confirmation
  happens explicitly client-side via the PaymentIntent's client secret.
  Webhook handling dropped the now-unused `checkout.session.completed` case
  and treats `incomplete_expired` subscriptions as canceled.
- ~~No way to refund a subscription~~ — fixed (0054adc):
  `POST /api/v1/admin/subscriptions/{userId}/refund` (full or partial via
  optional `amountCents`), alongside the existing grant/revoke/extend-trial
  admin actions. Resolves the subscription's paid invoice to a payment
  intent via `InvoicePayment` and issues a Stripe `Refund` against it; local
  subscription status is left untouched, matching how `revoke` is a separate
  explicit action.
- ~~No way to save a card independent of a subscription purchase~~ — fixed
  (09f7c50): `POST /api/v1/billing/payment-method/setup` issues a Stripe
  `SetupIntent` (`usage=off_session`) so a card can be tokenized and attached
  to the customer outside the checkout flow. A new `setup_intent.succeeded`
  webhook case sets the resulting payment method as the customer's default
  via `PaymentPort.setDefaultPaymentMethod`, so future subscription invoices
  can actually charge the saved card off-session.

Verified via updated `BillingServiceTest` (identity-service unit suite);
full `mvn test -pl services/identity-service -am` green. Not tracked against
a prior backlog entry — this was new scope, not a ported/audited item.

---

## [ ] I — Payment module hardening (idempotency, reconciliation, resilience)

**Status:** IN PROGRESS (2026-10-06). Prompted by mapping item H's billing module
against standard payment-interview architecture questions (industry MVP
checklist: idempotency, state machine, retries, reconciliation, provider
abstraction, PCI scope, observability — see `docs/PAYMENT_QUESTIONS.md`,
gitignored). Reviewed the actual code (`BillingService`,
`StripePaymentAdapter`, `PaymentPort`, `BillingController`) rather than
guessing; findings below are real gaps, not assumed ones. Ordered MVP-first
(cheapest, highest-risk items first), each independently shippable:

1. **No idempotency on `checkout` / `payment-method/setup` / `refund`.**
   A client retry (e.g. after a timeout) re-enters `BillingService` and
   calls Stripe again — `startCheckout` would create a second Stripe
   Subscription for the same user. This is exactly the flagship "customer
   clicks Pay once, gets charged twice" scenario. MVP fix: accept a client
   `Idempotency-Key` header on these three endpoints, record
   `(key, userId, endpoint) -> result` in a new small table before calling
   Stripe, short-circuit a repeat key to the stored result instead of
   re-calling. Pass the same key through to Stripe's own
   `RequestOptions.setIdempotencyKey(...)` as a second layer (currently
   never set — grepped, no idempotency key passed to Stripe anywhere in
   `StripePaymentAdapter`).
   **`checkout` and `refund` done (2026-10-06, uncommitted):**
   `Idempotency-Key` is now a required header on `POST /billing/checkout`
   and `POST /api/v1/admin/subscriptions/{userId}/refund`. A new
   `identity.idempotency_keys` table (`V4__add_idempotency_keys.sql`,
   `IdempotencyRecord` domain entity behind an `IdempotencyKeyRepository`
   port) reserves `(idempotencyKey, endpoint)` before the Stripe call via
   `jpa.save()`, relying on a DB unique constraint to reject a concurrent
   duplicate (translated to `IdempotencyConflictException` → 409); a
   completed reservation replays its stored result without touching Stripe
   again; a failed Stripe call deletes the reservation so the same key can
   be retried once the transient error clears — safe because Stripe's own
   idempotency key (now passed through on `Subscription.create` and
   `Refund.create`) still protects against a true duplicate even if our
   local row was cleared. A key presented by a different `userId` than the
   one who reserved it is rejected rather than replayed, to avoid leaking
   another user's checkout/refund result. `payment-method/setup` was left
   out of this pass — not asked for, and lower risk than checkout/refund
   since it never moves money by itself. Verified via 9 new
   `BillingServiceTest` cases (replay, in-progress conflict, cross-user
   rejection, release-on-failure, for both endpoints) — full
   `mvn test -pl services/identity-service -am` green (38 tests). Known
   gap carried over from the plan rather than silently fixed: the existing
   frontend (separate repo) doesn't send this header yet, so checkout will
   currently 400 there until it's updated to generate and send one.
2. **No webhook event dedup.** `PaymentPort.PaymentEvent` doesn't carry
   Stripe's event id, and `BillingService.applyEvent()` has no dedup check.
   Stripe can and does redeliver webhooks; today's handling happens to be
   mostly idempotent in effect (re-applying the same status is harmless),
   but that's incidental, not designed. Add the event id to `PaymentEvent`
   and a `processed_webhook_events` table checked before `applyEvent()`
   runs.
   **Done (2026-10-06, uncommitted):** `PaymentEvent` now carries Stripe's
   `event.getId()` (`StripePaymentAdapter.verifyWebhook`); a new
   `identity.processed_webhook_events` table
   (`V5__add_processed_webhook_events.sql`, `ProcessedWebhookEvent` entity
   behind a `ProcessedWebhookEventRepository` port) is checked at the top
   of `applyEvent()` — a known event id short-circuits to `true` (ack,
   nothing to redo) without touching `subscriptions`/`payments` again; the
   original switch logic moved unchanged into a private
   `applyEventEffects()`, recorded as processed only when it actually
   handled something (the `default` no-op case for untracked event types
   still isn't recorded — nothing to dedup there). Verified via 2 new
   `BillingServiceTest` cases (duplicate id skips reprocessing entirely;
   an unhandled event type still isn't recorded) plus existing event tests
   updated to assert the id *is* recorded on the handled path — full
   `mvn test -pl services/identity-service -am` green (39 tests).
3. **Crash-after-Stripe-call-before-save is unrecovered.** In
   `startCheckout`/`startPaymentMethodSetup`, if the process dies between
   the Stripe call succeeding and `subscriptions.save(sub)` persisting the
   Stripe customer/subscription id, the local row never links to Stripe —
   and the later `subscription_updated` webhook can't reconcile either,
   since `applyEvent` looks the subscription up **by** `stripeCustomerId`.
   MVP fix: a scheduled (or admin-triggered) reconciliation job that lists
   recent Stripe subscriptions for customers missing/mismatched locally and
   repairs the link — the standard "webhooks aren't enough, you still need
   a reconciliation job" answer from the question bank, made concrete here.
   **Done (2026-10-07):** driven from each configured processor's own
   subscription list rather than scanning local rows for a missing link
   (a null link is also just the normal state for any trialing user who
   hasn't subscribed yet, so it can't signal "broken" on its own) — every
   Stripe subscription already carries `user_id`/`plan` metadata
   (`StripePaymentAdapter.createSubscriptionIntent`), so reconciliation
   reads that back via a new `PaymentPort.listRecentSubscriptions(Instant)`
   (`PaymentPort.ReconciliationCandidate`; PayPal/Adyen return an empty
   list — not live yet, same as their other documented gaps in item 6
   below), repairs the local link if it's missing/mismatched, then replays
   the candidate through the already-tested `BillingService.applyEvent`
   path (synthetic `PaymentEvent` with `eventId=null` so the dedup check is
   skipped) to resync status/periodEnd too — a bonus beyond the strict
   link-repair scope, at near-zero extra cost since that data's already in
   hand. New `ReconciliationService` (`application/service/`) is called
   from both a scheduled `ReconciliationJob` (`adapter/out/scheduling/`,
   `@Scheduled`, 30 min default, mirroring observation-service's
   `OutboxPublisherJob` — same single-instance-replica caveat, lower-stakes
   here since reconciling is idempotent) and on demand via
   `POST /api/v1/admin/subscriptions/reconcile?lookbackHours=24`
   (`AdminController`, same `requireAdmin` convention as its siblings).
   `IdentityServiceApplication` gained `@EnableScheduling` (confirmed
   absent before this). Verified against the actual `stripe-java:29.0.0`
   jar on the classpath (not assumed) for the exact
   `Subscription.list`/`SubscriptionListParams.Created`/`autoPagingIterable`
   API shape. Verified via 5 new `ReconciliationServiceTest` cases
   (mismatched link repaired + resynced, already-linked skips the repair
   but still resyncs, missing-metadata candidate recorded as an error and
   skipped, one processor failing doesn't stop the others, no local row
   yet gets one created) + a `PaymentProcessorRegistryTest` case for the
   new `configured()` method — full `mvn test -pl services/identity-service
   -am` green (54 tests). No adapter-level test added for the new Stripe
   SDK call or a controller-level test for the new admin endpoint — neither
   has any existing precedent in this module to extend (no test anywhere
   mocks a Stripe SDK call directly, no `AdminControllerTest` file exists).
4. **No timeout/retry policy on Stripe SDK calls.** `StripePaymentAdapter`
   builds `RequestOptions` with just an API key; no connect/read timeout,
   no retry-with-backoff. Mirror the pattern already used for the AI
   service's `WebClient` (`WEAKNESS_AUDIT.md` §2.1): add an explicit
   timeout. Do **not** add blind retries on these calls — per the question
   bank's own framing, a failed charge/subscription-create must never be
   retried without the idempotency key from item 1 in place first, so this
   task is sequenced after item 1.
5. **Billing endpoints aren't rate-limited.** `RateLimitFilter` (added in
   item G, §1.2) only covers `/api/v1/species/**`. `/billing/checkout` and
   `/billing/payment-method/setup` call out to Stripe and are
   user-authenticated but still abusable; add them to the same filter's
   covered prefixes.

**Explicitly not doing (documented so it isn't re-proposed):** a local
payment-event audit ledger table independent of Stripe's own dashboard/API —
Stripe already is the system of record for raw transaction history here,
and duplicating it has real compliance/consistency cost; revisit only if
support/compliance needs querying payment history without hitting Stripe's
API. Not storing any card data locally is already correct (Stripe
Checkout/Elements + tokenization keep this service out of PCI SAQ D scope)
and needs no change.

6. **Multi-acquirer support added (2026-10-07): PayPal and Adyen now sit
   beside Stripe behind `PaymentPort`, routed through a new
   `PaymentProcessorRegistry`** (config-driven default + fallback for new
   checkouts; refunds/webhooks always go back to whichever processor
   actually owns the subscription, via the new `payment_processor` column
   added in `V6__add_payment_processor.sql`). Two gaps are intentionally
   left open rather than silently built or silently skipped:
   - **Adyen has no subscription object.** `AdyenPaymentAdapter` stores a
     payment method on first checkout (`recurringProcessingModel=
     SUBSCRIPTION`), but nothing charges it again on a monthly/yearly
     schedule — Adyen-sourced subscriptions will not actually renew until a
     recurring-charge scheduler (a new use case, not an adapter method) is
     built to charge the stored payment method on our own cadence. Do not
     route real customers to Adyen as a default processor before this
     exists.
   - **No adapter-level tests for PayPal/Adyen.** `BillingServiceTest` and
     `PaymentProcessorRegistryTest` cover the routing/dispatch logic, but
     nothing exercises `PayPalPaymentAdapter`/`AdyenPaymentAdapter` against
     real request/response shapes — there's no sandbox credential in this
     environment to verify against, and mocking their HTTP responses
     without ever having seen a real one would give false confidence. Add
     these once real PayPal/Adyen sandbox credentials are available.

7. **Broadening Stripe to Apple Pay/WeChat Pay investigated (2026-10-07) —
   Apple Pay needs no backend change, WeChat Pay was skipped.** Checked
   before writing any code, since both looked at first like a one-line
   `payment_method_types` change:
   - **Apple Pay: nothing to do here.** Stripe resolves an Apple Pay
     confirmation to an ordinary `type: card` payment method, so it already
     rides the same `Subscription.create(..., DEFAULT_INCOMPLETE)` path as
     any other card via Stripe's dynamic payment methods — no
     `StripePaymentAdapter` change. What's left is domain verification (the
     Apple Pay domain-association file hosted on the frontend's domain) and
     registering that domain in the Stripe Dashboard — `omyfish-frontend`/
     `omyfish-frontend-angular` territory, not this repo.
   - **WeChat Pay: skipped, not a fit for this billing model.** Two hard
     Stripe-side constraints rule it out as-is: it requires an explicit
     `payment_method_options.wechat_pay.client` (never auto-offered like
     Apple Pay), and it's excluded from `setup_future_usage` — Stripe's own
     support table lists it as incompatible with being stored for
     off-session/recurring reuse. `startCheckout` creates a `Subscription`
     directly (recurring by construction), so a WeChat Pay–originated
     checkout would work for exactly one period and then silently fail to
     renew — the same shape of gap already called out for Adyen above. If
     WeChat Pay is wanted later, it'd need a genuinely separate one-time
     top-up flow (new use case, new endpoint), not a bolt-on to subscription
     checkout.
