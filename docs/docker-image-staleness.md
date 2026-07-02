# Docker Image Staleness — Lesson & Trade-offs

## The incident (2026-07-02)

Photo upload returned HTTP 500. Root cause: the species-service **container was
running a 10-day-old image** built before the AI adapter contract fix
(`74d85cf`). The old code called `POST /predict?image_key=...` (query params);
the AI service expects a JSON body `{ "image_base64", "top_k" }` and answered
422, which surfaced as a 500 to the browser.

The source code was correct the whole time. The image was stale.

## The core lesson

**Container code lives in the image, not the container.** Therefore none of
these pick up code changes:

- `docker compose up -d` / `make up` — reuses existing images
- `docker compose restart <service>` — restarts the container on the same image
- Docker Desktop start/stop buttons
- **Deleting containers** — a new container is created from the same old image
- `make build` — runs Maven on the *host*; the Dockerfiles run Maven *inside*
  Docker, so host jars are never used by images

The only things that refresh code inside Docker are image builds:

```bash
# after pulling or changing code — rebuild everything that changed:
make build-up          # = docker compose up -d --build

# or targeted (single service):
docker compose build species-service && docker compose up -d species-service

# nuclear option (also wipes images):
docker compose down --rmi local && docker compose up -d --build
```

Note: deleting *images* (not containers) also works — `up` auto-builds any
image that is missing — but only for the images you remember to delete.

## Why `make up` does NOT include `--build` (deliberate)

We tried baking `--build` into `make up` and reverted it. The trade-offs:

| | `up -d` (current) | `up -d --build` |
|---|---|---|
| Stale-image risk | **Yes — you must remember to rebuild** | None |
| "Just start the stack" speed | Instant | Seconds (cached) to minutes (after edits) |
| Behavior matches convention | Yes (`up` = cheap start everywhere) | Surprising |
| Prod-like discipline | Yes (prod runs pinned, pre-built images) | No (build-on-start) |

Decision: keep `make up` cheap and predictable, and provide a **separate,
deliberate** target for rebuilds:

```makefile
up:        # daily, fast startup — reuses images
	docker compose up -d

build-up:  # when code, dependencies, or Dockerfiles changed
	docker compose up -d --build
```

The failure mode of forgetting is now documented here instead of hidden.

## Mitigations that make rebuilds cheap

The Java Dockerfiles were optimized so a manual `--build` costs little:

1. **Scoped COPY** — each Dockerfile copies all module `pom.xml`s (the Maven
   reactor needs them) but only its own service's `src/`. Editing one service
   no longer invalidates the other four images.
2. **Maven cache mount** — `RUN --mount=type=cache,target=/root/.m2,sharing=locked`
   persists downloaded dependencies across builds instead of re-downloading.

Measured: full cold rebuild of all 5 services ≈ 2m20s (one-time); rebuild after
a one-line change in one service ≈ **9s** (only that service recompiles).

Caveats:

- `docker builder prune` / `docker system prune` wipes the Maven cache mount —
  the next build re-downloads dependencies once. Harmless.
- Changes to `shared/` or any `pom.xml` rebuild all services (correct — they
  all depend on those).
- Repeated builds accumulate dangling layers; prune occasionally.

## Quick diagnostic

If behavior doesn't match the code, compare image age to the last commit:

```bash
docker inspect <container> --format '{{.Image}}' | xargs docker inspect --format '{{.Created}}'
git log -1 --format=%ci -- services/<service>
```

If the image predates the commit, it's stale — rebuild it.
