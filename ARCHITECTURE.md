# OMyFish Enterprise Java Architecture

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│   Browser (Next.js)    Mobile App (future)    Third-Party APIs              │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │ HTTPS
┌────────────────────────────────▼────────────────────────────────────────────┐
│                       API GATEWAY (Spring Cloud Gateway)                    │
│            JWT validation · Rate limiting · Routing · CORS                  │
└──────┬──────────────┬───────────────┬───────────────┬────────────────────── ┘
       │              │               │               │
┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────────┐
│  Identity   │ │  Species   │ │Observation │ │ Notification   │
│  Service   │ │  Service   │ │  Service   │ │   Service      │
│  Java 21   │ │  Java 21   │ │  Java 21   │ │   Java 21      │
│Spring Boot  │ │Spring Boot  │ │Spring Boot  │ │  Spring Boot  │
└──────┬──────┘ └──────┬─────┘ └──────┬─────┘ └────────────────┘
       │               │              │              ▲
       │               │ HTTP         │              │
       │               ▼              │              │
       │        ┌─────────────┐       │       ┌──────────────┐
       │        │  AI Service │       │       │   RabbitMQ   │
       │        │  Python 3.11│       │       │  (Quorum Q)  │
       │        │  FastAPI    │       │       └──────────────┘
       │        │  PyTorch    │       │              ▲
       │        │  ONNX RT    │       │              │
       │        └─────────────┘       │    Events published by:
       │                              │    - species-service (FishIdentified)
       ▼                              ▼    - observation-service (ObsCreated)
┌────────────────────────────────────────────────────────────────────────────┐
│                         PostgreSQL + PostGIS                                │
│     identity_db          species_db           observation_db                │
└────────────────────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────────────────────┐
│              MinIO (dev) / AWS S3 (prod) — Object Storage                  │
│                   omyfish-images    omyfish-exports                         │
└────────────────────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────────────────────┐
│             Observability Stack                                             │
│   OpenTelemetry Collector → Jaeger (traces)                                │
│   Micrometer → Prometheus → Grafana (metrics)                              │
│   Structured JSON logging → ELK / Loki                                     │
└────────────────────────────────────────────────────────────────────────────┘
```

## Microservice Decomposition

| Service              | Responsibility                                              | Port | Tech                          |
|----------------------|-------------------------------------------------------------|------|-------------------------------|
| **api-gateway**      | Routing, JWT validation, rate limiting, CORS                | 8080 | Spring Cloud Gateway          |
| **identity-service** | User registration, JWT issuance, OAuth2/OIDC, API keys     | 8081 | Spring Security, JJWT         |
| **species-service**  | AI orchestration, species knowledge base, top-K predictions | 8082 | Spring Web, Spring AMQP       |
| **observation-service** | Observation CRUD, EXIF extraction, PostGIS, GeoJSON export | 8083 | Hibernate Spatial, MinIO   |
| **notification-service** | Async notifications, email/webhook dispatch            | 8084 | Spring AMQP consumer          |
| **ai-service**       | EfficientNet-B3 inference, CLIP fallback, Bite Score forecast — shared `omyfish-ai` | 8000 | Python, FastAPI, PyTorch      |

## DDD Bounded Contexts

```
┌─────────────────────────────┐    ┌──────────────────────────────┐
│     IDENTITY CONTEXT        │    │     SPECIES CONTEXT          │
│                             │    │                              │
│  Aggregate: User            │    │  Aggregate: Species          │
│  Aggregate: ApiKey          │    │  Entity:    Prediction       │
│  ValueObj:  Email           │    │  ValueObj:  ConfidenceScore  │
│  ValueObj:  HashedPassword  │    │  Event:     FishIdentified   │
│  Port:      IssueToken      │    │  Port:      IdentifyFish     │
│  Port:      ValidateToken   │    │  Port:      GetSpecies       │
│                             │    │  Port:      GetBiteForecast  │
└─────────────────────────────┘    └──────────────────────────────┘

┌─────────────────────────────┐    ┌──────────────────────────────┐
│   OBSERVATION CONTEXT       │    │  NOTIFICATION CONTEXT        │
│                             │    │                              │
│  Aggregate: Observation     │    │  No domain model —           │
│  ValueObj:  GpsCoordinates  │    │  pure event consumer.        │
│  ValueObj:  ExifMetadata    │    │  Dispatches notifications    │
│  Event:     ObsCreated      │    │  in response to domain       │
│  Port:      CreateObs       │    │  events from other contexts. │
│  Port:      ExportGeoJson   │    │                              │
└─────────────────────────────┘    └──────────────────────────────┘
```

## Hexagonal Architecture (per service)

```
                    ┌───────────────────────────────────────┐
                    │         species-service               │
                    │                                       │
  REST Request ──► IN PORT ──► Application Service ──► OUT PORT ──► PostgreSQL
  RabbitMQ msg ──► IN PORT     (IdentificationService)  OUT PORT ──► AI Service
                    │          (SpeciesService)          OUT PORT ──► RabbitMQ
                    │                                       │
                    │   Domain Model (Species, Prediction,  │
                    │   ConfidenceScore — no framework deps)│
                    └───────────────────────────────────────┘

  IN Ports  = Java interfaces in domain/port/in/
  OUT Ports = Java interfaces in domain/port/out/
  Adapters  = implementations in adapter/in/ and adapter/out/
```

## RabbitMQ Event-Driven Workflow

```
User uploads photo
      │
      ▼
species-service  ──POST /api/v1/species/identify──►  AI Service (HTTP)
      │                                                    │
      │◄─────────── top-K predictions ───────────────────┘
      │
      ├── publishes: FishIdentifiedEvent
      │              exchange: omyfish.species
      │              routing_key: fish.identified
      │              queue: fish.identified.obs  (observation-service)
      │              queue: fish.identified.notif (notification-service)
      │              dlq: fish.identified.dlq
      │
      ▼
observation-service ─── logs result for audit
      │
      │ (user explicitly saves observation via POST /api/v1/observations)
      │
      ├── publishes: ObservationCreatedEvent
      │              exchange: omyfish.observations
      │              routing_key: observation.created
      │              queue: observation.created.notif
      │
      ▼
notification-service ─── sends email / push / webhook
```

## PostgreSQL + PostGIS Schema

### species_db

```sql
-- species table (see V1__create_species_table.sql)
species (id UUID PK, scientific_name, common_name, family,
         conservation_status, habitat, geographic_range,
         description, is_north_american_freshwater, created_at)

predictions (id UUID PK, species_id FK, image_storage_key,
             confidence DOUBLE, rank INT, predicted_at)
```

### observation_db

```sql
-- PostGIS-enabled (see V1__create_observations_table.sql)
observations (id UUID PK, user_id UUID, species_name,
              scientific_name, top_confidence,
              image_storage_key VARCHAR,
              location GEOMETRY(Point,4326),   -- PostGIS
              latitude, longitude,
              notes TEXT, observed_at, created_at)

-- Spatial index:
CREATE INDEX idx_observations_location ON observations USING GIST(location);
```

### GeoJSON Export Query

```sql
SELECT json_build_object(
  'type', 'FeatureCollection',
  'features', json_agg(
    json_build_object(
      'type', 'Feature',
      'geometry', ST_AsGeoJSON(location)::json,
      'properties', json_build_object(
        'id', id, 'species', species_name,
        'confidence', top_confidence, 'observedAt', observed_at
      )
    )
  )
) FROM observations
WHERE location IS NOT NULL
  AND observed_at BETWEEN :from AND :to;
```

## Object Storage Strategy

| Bucket              | Content                      | Lifecycle          |
|---------------------|------------------------------|--------------------|
| `omyfish-images`    | Raw uploaded fish photos     | 90-day archive     |
| `omyfish-thumbnails`| Resized 300×300 previews     | Generated on upload|
| `omyfish-exports`   | GeoJSON / CSV exports        | 7-day TTL          |
| `omyfish-models`    | ONNX model artifacts         | Version-tagged     |

## API Contracts (REST)

### species-service
```
POST /api/v1/species/identify
  Body: multipart/form-data { image: File, topK: int }
  Response: { predictions: [...], uncertain: bool, imageKey: string }

GET  /api/v1/species/{scientificName}
GET  /api/v1/species?family=&conservationStatus=&page=&size=
GET  /api/v1/species/bite-score/forecast?lat=&lon=&species=general&hours=336   (proxied to ai-service, powers the frontend /timing page)
GET  /api/v1/species/bite-score/today?lat=&lon=&species=general                (proxied to ai-service)
```

### observation-service
```
POST /api/v1/observations
  Body: { speciesName, imageKey, notes, latitude?, longitude? }
  Response: ObservationDto

GET  /api/v1/observations?userId=&species=&from=&to=&page=&size=
GET  /api/v1/observations/{id}
GET  /api/v1/observations/geojson?bbox=&from=&to=
DELETE /api/v1/observations/{id}
```

### identity-service
```
POST /api/v1/auth/register  { email, password, displayName }
POST /api/v1/auth/login     { email, password } → { accessToken, refreshToken }
POST /api/v1/auth/refresh   { refreshToken }
POST /api/v1/auth/api-keys  → { apiKey }
GET  /api/v1/auth/me
```

## Security Architecture

```
Client
  │── Bearer JWT ──► API Gateway
                          │── validates JWT signature (JJWT, RS256)
                          │── forwards X-User-Id, X-User-Roles headers
                          │── rate limits per IP + per user
                          ▼
                  Downstream services
                  (trust gateway headers, no re-validation)

JWT Payload: { sub: userId, email, roles: [...], exp, iat }
API Keys: hashed (bcrypt) in DB, validated in gateway, mapped to userId
OAuth2/OIDC: Spring Authorization Server for enterprise SSO (Phase 4)
```

## Observability

```
Each service:
  │── @Timed, @Counted (Micrometer) ──► Prometheus ──► Grafana dashboards
  │── OpenTelemetry Java agent ────────► OTEL Collector ──► Jaeger (traces)
  └── Logback JSON structured logging ─► Loki / ELK

Key metrics:
  - fish_identifications_total (counter, by species, by confidence_band)
  - identification_duration_seconds (histogram)
  - observations_created_total
  - ai_service_http_requests (latency, errors)
  - rabbitmq_messages_published / consumed
```

## Kubernetes Deployment

```
Namespace: omyfish
┌────────────────────────────────────────────────────────────┐
│  Ingress (nginx)                                           │
│    api.omyfish.io ──► api-gateway Service (ClusterIP)      │
└────────────────────────────────────────────────────────────┘
│  Deployments (HPA-managed):                                │
│    api-gateway        2–5 replicas   250m CPU  512Mi mem   │
│    identity-service   2–5 replicas   250m CPU  512Mi mem   │
│    species-service    2–20 replicas  500m CPU  1Gi mem     │
│    observation-service 2–10 replicas 250m CPU  512Mi mem   │
│    notification-service 1–3 replicas 100m CPU  256Mi mem   │
│    ai-service         1–4 replicas   1 CPU     4Gi mem     │
│                                                            │
│  StatefulSets: postgres, rabbitmq                          │
│  PersistentVolumeClaims: postgres 20Gi, minio 50Gi         │
│  ConfigMaps: per-service application.yml                   │
│  Secrets: DB passwords, JWT secret, S3 keys (via Vault)   │
└────────────────────────────────────────────────────────────┘
```

## Scaling Strategy

| Users     | species-service | observation-service | ai-service | DB           | Notes                          |
|-----------|-----------------|---------------------|------------|--------------|--------------------------------|
| 10        | 1 replica       | 1 replica           | 1 replica  | Single node  | Docker Compose, no HPA needed  |
| 100       | 2 replicas      | 2 replicas          | 1 replica  | Single node  | K8s, HPA enabled               |
| 1,000     | 4 replicas      | 3 replicas          | 2 replicas | Read replica | Connection pooling (PgBouncer) |
| 100,000   | 20 replicas     | 10 replicas         | 4 replicas | Citus/Aurora | CDN, Redis cache, async writes |

JVM Tuning (production):
- Java 21 Virtual Threads (Project Loom) for I/O-bound services
- `-XX:+UseZGC` for low-pause GC on services with large heaps
- Container-aware: `-XX:MaxRAMPercentage=75`

## Technology Selection Rationale

**Java 21 + Spring Boot 3.x** — Java 21 LTS brings Virtual Threads (Project Loom), making blocking I/O as scalable as reactive without the complexity of WebFlux. Spring Boot 3.x is the dominant enterprise Java framework with a vast ecosystem, production battle-testing, and first-class observability support via Micrometer.

**Hexagonal Architecture** — Ports & Adapters keeps domain logic free from framework dependencies. Swapping RabbitMQ for Kafka, PostgreSQL for another DB, or MinIO for S3 requires only adapter changes — the domain model and use cases are untouched. Critical for a platform expected to evolve for years.

**Spring AMQP + RabbitMQ Quorum Queues** — Quorum queues provide HA and durability guarantees (Raft consensus) unlike classic mirrored queues. Spring AMQP's `@RabbitListener` + DLQ/retry policy gives resilient async processing with minimal boilerplate.

**Hibernate Spatial + PostGIS** — Hibernate Spatial adds JPA support for `Geometry` types, enabling type-safe GIS queries in Java code. PostGIS's ST_AsGeoJSON, ST_Within, ST_DWithin make radius searches and GeoJSON export trivially efficient.

**omyfish-ai (shared AI microservice)** — PyTorch's ecosystem for computer vision has no Java equivalent. The AI service lives in its own repo (`../omyfish-ai`) and is shared across omyfish-python, omyfish-java, and omyfish-dotnet. The docker-compose build context points to `../omyfish-ai` so the data science team can iterate on the model independently of the Java release cycle. ONNX export enables future edge deployment.

**Flyway** — Schema-as-code with versioned SQL migrations. Runs automatically on service startup, supports rollback scripts, and integrates with GitLab CI for migration dry-runs in staging before production applies.

**Micrometer + OpenTelemetry** — Micrometer is the de-facto metrics facade for Spring apps (analogous to SLF4J for logging). Pairing it with the OpenTelemetry Java agent gives vendor-neutral traces without changing application code.

## Migration Roadmap from Python

### Phase 1 — Foundation (Weeks 1–4)
- Set up Maven multi-module structure
- Deploy PostgreSQL + PostGIS, RabbitMQ, MinIO via Docker Compose
- Implement identity-service (auth, JWT)
- Keep Python AI service running as-is

### Phase 2 — Core Domain (Weeks 5–8)
- Implement species-service with hexagonal architecture
- Wire AI service HTTP client (reuse existing Python predictor)
- Publish FishIdentifiedEvent to RabbitMQ
- Seed species knowledge base from existing `fish_info.json`

### Phase 3 — Observations (Weeks 9–12)
- Implement observation-service with PostGIS
- EXIF extraction adapter (Apache Commons Imaging)
- MinIO storage adapter
- GeoJSON export endpoint
- Migrate existing SQLite observations to PostgreSQL

### Phase 4 — Frontend (Weeks 13–16)
- Next.js 15 + TypeScript frontend
- MapLibre GL JS observation map
- Replace Streamlit UI
- Mobile-first responsive design

### Phase 5 — Observability & Security (Weeks 17–20)
- OpenTelemetry agent, Micrometer metrics
- Prometheus + Grafana dashboards
- Jaeger distributed tracing
- API key management for external integrations
- OAuth2/OIDC with Spring Authorization Server

### Phase 6 — Kubernetes & Production (Weeks 21–24)
- Helm chart deployment
- HPA for all services
- GitLab CI/CD pipeline
- Load testing (k6) to validate scaling strategy
- Decommission Python FastAPI app layer (keep AI service)

## Production Readiness Checklist

- [ ] All services have /actuator/health liveness + readiness probes
- [ ] JWT secret stored in Kubernetes Secret (not ConfigMap)
- [ ] Flyway migration tested on production schema snapshot
- [ ] RabbitMQ Quorum queues configured (not classic)
- [ ] Dead Letter Queues configured for all consumers
- [ ] PgBouncer connection pooling in front of PostgreSQL
- [ ] MinIO/S3 lifecycle rules for image archival
- [ ] Structured JSON logging to centralized sink
- [ ] OpenTelemetry traces sampling rate configured (10% prod)
- [ ] HPA tested under load (k6 ramp test)
- [ ] CORS restricted to known origins (not `*`)
- [ ] Rate limiting configured in API Gateway
- [ ] Secrets rotation procedure documented
- [ ] Database backup + restore tested
- [ ] GracefulShutdown enabled (Spring Boot `server.shutdown=graceful`)

## Cost Evolution

| Stage      | Users  | Monthly Est. | Stack                                                        |
|------------|--------|-------------|--------------------------------------------------------------|
| MVP        | 10     | ~$50        | Single EC2 t3.medium, Docker Compose, RDS micro              |
| Growth     | 1,000  | ~$400       | EKS 3-node cluster, RDS db.t3.large, ElastiCache             |
| Scale      | 10,000 | ~$1,500     | EKS 6-node, RDS db.r6g.large + read replica, CloudFront CDN |
| Enterprise | 100,000| ~$6,000     | EKS 20-node, Aurora PostgreSQL, multi-AZ, WAF, DDoS shield   |
