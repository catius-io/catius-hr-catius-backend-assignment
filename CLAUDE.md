# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

Hiring assignment scaffold for a two-service order/inventory mini-MSA. Candidates fork this repo and implement the business logic; the scaffold ships only the Gradle multi-project layout, Spring Boot wiring, and empty package directories (controller/service/domain/repository are all `.gitkeep` placeholders). Treat all production code as something to be **added**, not refactored from existing implementations.

## Assignment Context (from README.md)

This is a **Catius backend hiring assignment**. Submission flow: fork → implement on a feature branch → open PRs against the candidate's own fork `main` → submit fork URL + PR URLs + final commit SHA by email.

### Required Capabilities to Build

The candidate must turn the empty scaffold into a working two-service system that demonstrates:

1. **Order creation flow (Saga starting point)** — `POST /api/v1/orders` triggers: reserve inventory → confirm order → publish `order.order-confirmed.v1` Kafka event. On any failure after reservation, compensate by calling `POST /api/v1/inventory/release`.
2. **Inter-service communication** — order-service calls inventory-service over OpenFeign, wrapped in Resilience4j (circuit breaker / time limiter / retry). Configuration values must be deliberately chosen and justified, not left at defaults.
3. **Concurrency control on inventory** — inventory decrement is the contention point. Candidate picks optimistic vs. pessimistic locking and documents the rationale; concurrency tests must prove stock cannot go negative under parallel reservations.
4. **Saga compensation** — partial-failure paths (reservation succeeded but confirmation/event publish failed) must trigger inventory release; behavior under concurrent failures must hold.
5. **Tests** — controller (MockMvc), service unit tests, Saga integration tests using `@EmbeddedKafka`, and concurrency scenarios.
6. **Performance** — at least one k6 scenario under `perf/scenarios/` with ramp-up/steady/ramp-down stages and explicit SLO thresholds (p95 latency, error rate) that fail the run when violated.
7. **Design documentation** — README must explain *why* for: lock strategy, circuit-breaker numbers, topic naming, failure modes, Saga style choice.

### Evaluation Rubric (total 100%)

| Area | Weight | What Reviewers Look For |
|---|---|---|
| **Functional behavior** | 25% | End-to-end success and failure scenarios actually work |
| **Inter-service design** | 20% | Feign + circuit breaker + timeout + retry values backed by reasoning |
| **Saga compensation robustness** | 15% | Partial failures and concurrency scenarios handled correctly |
| **Performance test realism & interpretation** | 15% | Scenario design, target metric justification, depth of result analysis |
| **Code quality & tests** | 15% | Layer separation, naming, presence of unit + integration tests |
| **Design documentation & tradeoffs** | 10% | README structure, justified design choices |

**Penalty signals**: a single lump commit, no tests at all, no README, missing core requirements (circuit breaker / Saga), thin PR descriptions.

### Fixed Constraints (DO NOT change)

- **Stack**: Java 21 (or Kotlin 1.9+), Spring Boot 3.3.x, Spring Data JPA + SQLite, OpenFeign + Resilience4j, Spring for Apache Kafka + `@EmbeddedKafka` for tests, k6 for load tests, Gradle multi-project (wrapper 8.10.2).
- **Kafka in tests**: Use `@EmbeddedKafka` — running Kafka via docker-compose is explicitly **discouraged**. If unavoidable, the candidate must document why.
- **Database**: SQLite is intentional. Do not swap to H2/in-memory just because it would be easier — the file-based persistence and SQLite's single-writer file lock are part of the constraint set the design must reckon with.

### FAQ Highlights (Strategic Hints from README)

- *"Inventory looks too simple — should I add complexity?"* → **No.** Build the simplest implementation that satisfies the core requirements; spend remaining time on tests, docs, and performance interpretation.
- *"Choreography vs. Orchestration Saga?"* → Either is acceptable. **The choice must be justified in the README.**
- *"Are AI tools allowed?"* → Yes, but **the candidate must be able to explain every line in the follow-up interview**. Do not include code that cannot be defended verbally.
- *"What if I run out of time?"* → Don't force-finish everything. Focus on the core, and **honestly list what was intentionally skipped** in a README section. Judgment is also evaluated.

### Implementation Order Heuristic

Because order-service depends on inventory-service (Feign calls) and the only real concurrency problem lives in inventory:
1. inventory-service domain + JPA + lock + 3 REST endpoints + concurrency test
2. order-service domain + Feign client + Resilience4j config
3. order-service Saga logic + Kafka publisher
4. `@EmbeddedKafka` integration tests + k6 scenario + README rationale sections

Building inventory first means order can call a real, tested dependency instead of mocks that drift from the eventual contract.

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

## Commit & PR Conventions

Conventions are derived from the existing git history of this fork and from the constraint above; keep them stable across sessions so commit history reads consistently to the reviewer.

- **Message format**: `<type>(<scope>): <Korean summary>` matching existing history. Allowed types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`.
- **Scope must be a domain or file area** — examples: `gradle`, `inventory`, `order`, `saga`, `feign`, `kafka`, `guide`, `readme`, `gitignore`. **Never use an AI tool name** (`claude`, `copilot`, etc.) as a scope; that conflates "the file being touched" with "who wrote it" and reads ambiguously to the reviewer.
- **AI disclosure**: keep the `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` trailer on AI-assisted commits. README FAQ explicitly allows AI tools provided the candidate can defend the code in interview — honest disclosure aligns with that policy and avoids any appearance of hiding AI usage.
- **Granularity**: prefer small, topic-scoped commits. The rubric penalizes "한 덩어리 커밋" (a single lump commit). When work spans multiple concerns (e.g. domain entity + repository + service + controller), split into separate commits and ideally separate PRs.
- **Branch naming**: `<type>/<short-kebab-summary>` (e.g. `chore/dev-env-setup`, `docs/claude-md-assignment-context`, `feature/inventory-domain`). Keep `feature/*` reserved for actual rubric-graded functionality; environment, docs, and tooling work goes under `chore/*` or `docs/*` so PR lists are easy to scan.
- **PR body** (Korean) should include three sections:
  - `## Summary` — bullet list of what changed
  - `## Why` — motivation tying back to the rubric or a concrete need
  - `## Test plan` — checklist of how to verify
- **Force-push policy**: only `--force-with-lease`, and only on un-merged branches owned by the candidate's fork. Never rewrite history on `main` once a PR has been merged.