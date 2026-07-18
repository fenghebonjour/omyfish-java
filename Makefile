.PHONY: up down build test migrate logs clean fmt

# ─── Dev environment ──────────────────────────────────────────────────────────

up:
	docker compose up -d

# Use when code, dependencies, or Dockerfiles changed — rebuilds images first
build-up:
	docker compose up -d --build

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
	  mvn test -pl services/identity-service,services/species-service,services/observation-service,services/notification-service --no-transfer-progress

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
	mvn flyway:migrate -pl services/species-service
	mvn flyway:migrate -pl services/observation-service
	mvn flyway:migrate -pl services/identity-service

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
