.PHONY: up build-up check-frontend-pin down build test migrate logs clean fmt

# ─── Dev environment ──────────────────────────────────────────────────────────

up:
	@$(MAKE) --no-print-directory check-frontend-pin
	docker compose up -d

# Use when code, dependencies, or Dockerfiles changed — rebuilds images first
build-up:
	@$(MAKE) --no-print-directory check-frontend-pin
	docker compose up -d --build

# Warns (doesn't block) if docker-compose.yml's pinned frontend image tag is behind
# a locally-cloned ../omyfish-frontend checkout — the gotcha where a frontend feature
# lands but nothing shows up until the pin is bumped.
check-frontend-pin:
	@FRONTEND_PIN=$$(grep -oE 'omyfish-frontend:[a-f0-9]+' docker-compose.yml | head -1 | cut -d: -f2); \
	if [ -d ../omyfish-frontend/.git ]; then \
		FRONTEND_HEAD=$$(git -C ../omyfish-frontend rev-parse --short HEAD 2>/dev/null); \
		if [ -n "$$FRONTEND_HEAD" ] && [ -n "$$FRONTEND_PIN" ] && [ "$$FRONTEND_PIN" != "$$FRONTEND_HEAD" ]; then \
			echo "warning: docker-compose.yml pins frontend image tag $$FRONTEND_PIN, but ../omyfish-frontend is at $$FRONTEND_HEAD — bump the pin (see README) to pick up frontend changes"; \
		fi; \
	fi

down:
	docker compose down

restart:
	docker compose down && docker compose up -d

logs:
	docker compose logs -f $(service)

ps:
	docker compose ps

# ─── Build ────────────────────────────────────────────────────────────────────

build:
	mvn clean package -DskipTests

build-docker:
	docker compose build

build-service:
	mvn clean package -DskipTests -pl services/$(service) -am

# ─── Test ─────────────────────────────────────────────────────────────────────

# Run all unit + slice tests via Docker (no local Maven required)
test:
	docker run --rm \
	  -v $(CURDIR):/workspace \
	  -v omyfish-maven-cache:/root/.m2 \
	  -w /workspace \
	  maven:3.9-eclipse-temurin-21-alpine \
	  mvn test -pl services/identity-service,services/species-service,services/observation-service,services/notification-service,services/shop-service --no-transfer-progress

test-service:
	docker run --rm \
	  -v $(CURDIR):/workspace \
	  -v omyfish-maven-cache:/root/.m2 \
	  -w /workspace \
	  maven:3.9-eclipse-temurin-21-alpine \
	  mvn test -pl services/$(service) -am --no-transfer-progress

test-integration:
	mvn verify -Pintegration-tests

# ─── Database ─────────────────────────────────────────────────────────────────

migrate:
	mvn flyway:migrate -pl services/observation-service
	mvn flyway:migrate -pl services/identity-service
	mvn flyway:migrate -pl services/shop-service

migrate-service:
	mvn flyway:migrate -pl services/$(service)

# ─── Utilities ────────────────────────────────────────────────────────────────

fmt:
	mvn spotless:apply

lint:
	mvn spotless:check checkstyle:check

clean:
	mvn clean
	docker compose down -v

shell-postgres:
	docker compose exec postgres psql -U omyfish -d omyfish

shell-rabbitmq:
	docker compose exec rabbitmq rabbitmqctl status

minio-create-buckets:
	docker compose exec minio mc alias set local http://localhost:9000 omyfish omyfish_dev
	docker compose exec minio mc mb local/omyfish-images --ignore-existing
	docker compose exec minio mc mb local/omyfish-exports --ignore-existing
