# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

Hiring assignment scaffold for a two-service order/inventory mini-MSA. Candidates fork this repo and implement the business logic; the scaffold ships only the Gradle multi-project layout, Spring Boot wiring, and empty package directories (controller/service/domain/repository are all `.gitkeep` placeholders). Treat all production code as something to be **added**, not refactored from existing implementations.

## Common Commands

```bash
# Full build + tests (root)
./gradlew build

# Run a single service
./gradlew :order-service:bootRun
./gradlew :inventory-service:bootRun

# Boot order-service without trying to connect to an external Kafka broker
./gradlew :order-service:bootRun --args='--spring.kafka.listener.auto-startup=false'

# Tests for one module / single test class / single method
./gradlew :order-service:test
./gradlew :order-service:test --tests com.catius.order.SomeTest
./gradlew :order-service:test --tests com.catius.order.SomeTest.shouldDoX

# Performance (k6 must be installed locally)
k6 run perf/scenarios/create-order.js
```

JDK 21 is required locally (Gradle toolchain pins `JavaLanguageVersion.of(21)`).

## Architecture

Two Spring Boot 3.3 services managed as Gradle subprojects (`settings.gradle` includes only `order-service` and `inventory-service`). Common dependencies live in the root `build.gradle`'s `subprojects { }` block; per-service `build.gradle` files only override `bootJar` main class / artifact name.

**Communication topology (intended end-state):**
- `order-service` (`:8081`) is the Saga starter. It calls `inventory-service` synchronously over **OpenFeign** (base URL `${INVENTORY_BASE_URL:http://localhost:8082}`) wrapped with **Resilience4j** (`circuitbreaker` / `timelimiter` / `retry` instances all named `inventoryClient` in `application.yml`).
- After successful reservation + order confirmation, `order-service` publishes `order.order-confirmed.v1` to Kafka (topic name configured under `catius.kafka.topics.order-confirmed`).
- On reservation success but later failure, the Saga must compensate via `POST /api/v1/inventory/release`. Saga style (orchestration vs. choreography) is a candidate decision and must be justified in README.

**Persistence:** Spring Data JPA over **SQLite** using `org.hibernate.community.dialect.SQLiteDialect`. Each service writes its own DB file (`order-service.db`, `inventory-service.db`) into the working directory; these are gitignored. `ddl-auto: update` is on, so schema evolves with entity changes — be deliberate about column types/constraints since SQLite's type affinity will silently accept mismatches.

**Concurrency:** Inventory decrement is the contention point. The assignment requires choosing optimistic vs. pessimistic locking and documenting the rationale; SQLite serializes writes at the file level, which constrains realistic pessimistic-lock benchmarks.

**Kafka:** Production config points at `${KAFKA_BROKERS:localhost:9092}` with `auto-startup: true`. Tests are expected to use `@EmbeddedKafka` from `spring-kafka-test` rather than docker-compose (per README guidance). When running `bootRun` locally without a broker, disable the listener via the `--args` flag above to avoid connect-loop noise.

**Observability:** Actuator exposes `health,info,prometheus,metrics` on both services; order-service additionally exposes `circuitbreakers`. Micrometer → Prometheus is wired by default.

## Package Conventions

Each service follows the same layout under `com.catius.{order,inventory}`:
- `controller/` — REST endpoints. README describes the contract (`POST /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET/POST /api/v1/inventory/...`) but the directories are currently empty `.gitkeep`s. When adding controllers, also add the exception → HTTP mapping.
- `service/` — business logic, Feign calls, Saga orchestration, Kafka publish.
- `domain/` — entities, value objects, domain events.
- `repository/` — Spring Data JPA interfaces.

Lombok is on the classpath (compileOnly + annotationProcessor) for both `main` and `test`.

## Resilience4j Defaults

`order-service/src/main/resources/application.yml` ships baseline numbers for the `inventoryClient` instance (sliding window 10, failure threshold 50%, timeout 2s, retry max 3 / wait 500ms on `IOException`/`TimeoutException`). These are **starting points** — the rubric explicitly grades whether the candidate adjusts them with documented justification.

## Submission Constraints That Affect Code Style

The rubric (README §평가 루브릭) penalizes commits squashed into one lump and missing tests. When making changes, prefer **small, topic-scoped commits** and keep test coverage in lockstep with new logic. README also flags missing design rationale (lock strategy, CB numbers, topic naming, failure modes) as a deduction — surface those as comments or doc updates when introducing the relevant code.