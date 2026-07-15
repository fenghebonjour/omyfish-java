# CLAUDE.md — OMyFish Enterprise Java

## Commands

```bash
make up                         # start all Docker services (reuses images — does NOT rebuild)
make build-up                   # rebuild images + start (use after code changes)
make down                       # stop all services
make build                      # mvn clean package -DskipTests (host-side; does not affect Docker images)
make test                       # mvn test (all modules)
make test-service service=species-service  # single service
make migrate                    # run Flyway migrations (all services)
make logs service=species-service          # tail logs
make shell-postgres             # psql into omyfish DB
make fmt                        # spotless:apply
```

```bash
# Build single module
mvn clean package -DskipTests -pl services/species-service -am

# Run a service locally (outside Docker)
cd services/species-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Repository Structure

```
pom.xml                         parent POM — dependency management
shared/
  omyfish-shared-domain/        Entity, AggregateRoot, DomainEvent, ValueObject
  omyfish-shared-events/        FishIdentifiedEvent, ObservationCreatedEvent
services/
  api-gateway/                  Spring Cloud Gateway — routes, auth filter
  identity-service/             JWT issuance, user registration, API keys
  species-service/              AI orchestration, species KB
  observation-service/          Observation CRUD, EXIF, PostGIS, GeoJSON
  notification-service/         RabbitMQ consumer, notifications
frontend/omyfish-web/           Next.js 15 + TypeScript
infrastructure/
  kubernetes/                   K8s manifests (namespace, deployment, hpa, ingress)
  helm/omyfish/                 Helm chart for all services
```

## Architecture: Hexagonal (Ports & Adapters)

Each Java service follows:
```
domain/
  model/          Aggregates, Entities, Value Objects — zero framework deps
  event/          Domain events (extend DomainEvent from shared-domain)
  port/in/        Use case interfaces (IdentifyFishUseCase, etc.)
  port/out/       Repository + external service interfaces
application/
  service/        Use case implementations — only domain + port deps
adapter/
  in/web/         @RestController or @RabbitListener
  out/persistence/ Spring Data JPA repositories
  out/messaging/  RabbitMQ publishers (implements domain port)
  out/external/   HTTP clients to AI service, external APIs
config/           Spring beans wiring ports to adapters
```

**Rule:** `domain/` and `application/` must not import Spring annotations or JPA entities.

## Database

- Flyway migrations: `src/main/resources/db/migration/V{n}__description.sql`
- PostGIS queries: use `ST_AsGeoJSON`, `ST_Within`, `ST_DWithin` directly in JPQL/native queries
- Each service owns its own schema (logical separation in same PostgreSQL instance for dev)

## RabbitMQ

- Exchanges: `omyfish.species`, `omyfish.observations`
- Routing keys: `fish.identified`, `observation.created`
- All queues are Quorum type with DLQ configured
- Event classes live in `shared/omyfish-shared-events/`

## AI Service

The Python AI service (built from `../omyfish-ai` — see docker-compose.yml) is called via HTTP from `species-service`.
- Adapter: `adapter/out/external/AIServiceAdapter.java`
- Port: `domain/port/out/AIServicePort.java`
- Do not add PyTorch/ML dependencies to any Java service.

Besides fish ID (`POST /predict`), ai-service exposes the Bite Score forecast (`GET /bite-score/forecast|today|species-key`). Bite-score responses always include a six-factor breakdown — pass it through to clients untouched, never reduce it to just the headline score. `GET /bite-score/species-key?name=` maps a confirmed fish ID to the species key to store per user for tuned forecasts.

## Key Spring Dependencies (managed in parent pom.xml)

- Spring Boot 3.3.x BOM
- Spring Cloud 2023.0.x BOM (Gateway)
- `spring-boot-starter-data-jpa` + `hibernate-spatial` (PostGIS)
- `spring-boot-starter-amqp` (RabbitMQ)
- `spring-boot-starter-security` + `jjwt` (JWT)
- `flyway-database-postgresql` (migrations)
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- `opentelemetry-spring-boot-starter`

## Testing

- Unit: plain JUnit 5, Mockito — no Spring context
- Integration: `@SpringBootTest` + Testcontainers (PostgreSQL, RabbitMQ)
- Slice tests: `@WebMvcTest`, `@DataJpaTest` for fast feedback
