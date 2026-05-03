# ADR-007: 이벤트 발행과 트랜잭션 일관성

- **상태**: 확정
- **결정**: **이벤트 토픽별로 발행 전략 분할**.
  - **Fan-out 이벤트 (`order.order-confirmed.v1`)**: 직접 발행. `KafkaTemplate.send()`을 `@Transactional` 커밋 직후 호출. dual-write 윈도우는 producer retries + 컨슈머 멱등 키로 mitigation.
  - **보상 이벤트 (`inventory.release-requested.v1`)**: **`pending_compensations` 테이블로 영속화한 뒤 발행 (boundary가 단일 토픽에 제한된 축소 outbox)**. 발행 실패 시 부팅 시점 재시도. 풀스택 outbox는 도입하지 않으며 보상 토픽에만 적용.

## 근거

- **SQLite 환경에서 outbox의 robust 변형이 차단됨**:
  - SQLite는 `LISTEN/NOTIFY` 또는 `pg_notify`에 대응하는 트랜잭션 기반 NOTIFY 메커니즘이 없음. 외부 리스너에게 트랜잭션 단위 신호를 보낼 길이 없음.
  - SQLite의 `sqlite3_update_hook`은 C API에만 존재하고 JDBC 미노출. Java 레벨에서 구독 불가.
  - log-based CDC(Debezium)도 SQLite connector 미지원.
  - 결과적으로 SQLite 환경에서 가능한 outbox 구현은 **순수 polling publisher**가 유일.
- **Polling outbox는 outbox의 가장 약한 변형**:
  - 즉시성 부재(폴링 주기만큼 latency 추가).
  - 폴링 부하·outbox 테이블 cleanup·다중 인스턴스 leader election 등 운영 항목이 추가되는데, 이를 회피하려고 단순화하면 결국 robustness가 producer retries 수준으로 수렴.
  - 본 과제 핵심 영역(Saga 견고성·통신 설계·성능)에 직접 기여하는 가치가 작아 ROI 부족.
- **본 과제의 발행 실패 영향 범위가 이벤트별로 다름**:
  - Forward 경로는 sync HTTP라 발행 실패가 사용자 응답에 즉시 반영됨 — 이미 명확한 실패 시그널.
  - Fan-out 이벤트(`order.order-confirmed.v1`)는 본 과제에 컨슈머가 부재해 회귀 표면 자체가 없음.
  - **보상 이벤트(`inventory.release-requested.v1`)는 발행 실패 시 재고 영구 leak**으로 직결되며, 다중 item 부분 성공 시(ADR-003) 손실 영향이 N개 product로 확대됨. 이 이벤트만은 별도 mitigation 필요 — 하단 "보상 이벤트 손실 mitigation" 절 참조.

## 보상 이벤트 손실 mitigation

다중 item 부분 성공 후 보상 이벤트 발행 직전에 프로세스가 다운되는 경우, **이 영속화가 없다면** in-memory 보상 후보가 사라져 N개 product의 재고가 영구 차감된다. outbox를 도입하지 않은 트레이드오프의 가장 큰 비용이라 별도 mitigation 명시. 아래 `pending_compensations` 테이블이 보상 후보를 호출 직전 영속화하는 역할을 한다.

### 스키마

```
pending_compensations (
  order_id           PK,
  attempted_items_json  (HTTP 호출을 시도한 items[] — 응답 받기 전부터 포함)
  status             ENUM(IN_PROGRESS, READY_TO_PUBLISH, PUBLISHED, COMPLETED, DISPATCH_FAILED)
  reason             ENUM(EXPLICIT_FAILURE, AMBIGUOUS_FAILURE, PERSIST_FAILURE, CRASH_RECOVERY) NULLABLE
  created_at, updated_at, last_attempt_at, attempt_count
)
```

### 라이프사이클 (다중 item Saga와 동기)

forward 알고리즘(ADR-003)의 각 분기마다 **order-service의 별도 local DB 트랜잭션**으로 row를 갱신한다. inventory.reserve는 원격 HTTP라 order-service의 DB 트랜잭션과 묶을 수 없으므로, 각 reserve 호출 전후로 별도 commit이 필요하다.

**핵심 원칙 (두 축)**:

1. **HTTP 호출 시작 전 attempted append** (응답을 못 받아도 기록이 남도록). 응답 후 분류:
   - explicit 4xx → 차감 없음 확정 → attempted_items에서 해당 item 제거
   - ambiguous (timeout/5xx) → 차감 여부 불명확 → attempted_items에 보존
   - 응답 받기 전 crash → recovery 시 attempted_items 전체를 ambiguous로 간주 (release 멱등성이 받쳐줌)

2. **COMPLETED는 Order INSERT와 같은 트랜잭션** — confirmed 이벤트 발행 성공 여부와 분리. 이유: confirmed 발행 실패 시 사용자에게 201 반환하면서 `pending_compensations`가 IN_PROGRESS로 남아있으면, 부팅 복구 로직이 이미 확정된 주문의 재고를 부당 release할 수 있음. Order persist 트랜잭션에 COMPLETED 마킹을 묶어 이 race를 차단.

| 시점 | 동작 |
|---|---|
| 첫 reserve 호출 **직전** | INSERT `(order_id, attempted_items_json=[], status=IN_PROGRESS)`. row 자체가 "이 주문이 보상 가능 상태로 진입함"을 의미 |
| 각 reserve 호출 **직전** (i번째) | `attempted_items_json`에 items[i] append. status IN_PROGRESS 유지 |
| reserve **성공 후** | 별도 갱신 없음 (이미 attempted_items에 들어가 있음). 다음 i+1번째 호출로 넘어감 |
| **명시적 4xx (i번째)** | `attempted_items_json`에서 items[i] 제거 → 잔존 attempted가 비어있으면 `status=COMPLETED` (보상 불필요), 비어있지 않으면 `status=READY_TO_PUBLISH, reason=EXPLICIT_FAILURE` |
| **불명확 실패 (i번째)** | items[i]는 attempted_items에 그대로 보존 → `status=READY_TO_PUBLISH, reason=AMBIGUOUS_FAILURE` |
| **reserve 전부 성공 후 Order persist 시도 직전** | status는 여전히 IN_PROGRESS. attempted_items에는 N개 items 전부 들어있음 |
| **Order INSERT + COMPLETED 마킹** (단일 order-service local transaction) | Order INSERT와 `pending_compensations.status=COMPLETED`를 **같은 트랜잭션 내에서** 커밋. 트랜잭션 커밋 성공 = 정상 흐름 종료 (보상 복구 대상에서 빠짐). 이후 confirmed 이벤트 발행은 별개 단계로 진행되며 발행 실패 여부와 무관하게 보상 복구를 트리거하지 않음 |
| **Order persist 실패** | 트랜잭션 롤백 → `pending_compensations.status=READY_TO_PUBLISH, reason=PERSIST_FAILURE` (attempted_items 그대로) |
| **confirmed 이벤트 발행 성공/실패** | pending_compensations 상태에 영향 없음 (이미 COMPLETED). 발행 실패는 로그/메트릭만, 사용자 응답은 201. 상세는 하단 "Confirmed 이벤트 발행 실패 정책" |
| **READY_TO_PUBLISH 발행 시도** | KafkaTemplate.send → 성공 시 `status=PUBLISHED` |
| **발행 실패** | `status=DISPATCH_FAILED, attempt_count++` + ERROR 로그 + 메트릭(`compensation_dispatch_failed_total`) |
| **부팅 시 복구** | `status IN (IN_PROGRESS, READY_TO_PUBLISH, DISPATCH_FAILED)` 스캔. IN_PROGRESS row는 프로세스가 reserve 흐름 중간에 죽었음을 의미 → `reason=CRASH_RECOVERY`로 표시 후 attempted_items 기준 보상 발행. **COMPLETED row는 스캔 대상 아님** — 정상 확정된 주문의 재고를 부당 release하지 않도록 보장 |

> **재고 leak 방지의 핵심**: HTTP 호출 시작 전에 attempted_items append가 commit된다. 따라서 (1) 호출 도중 crash, (2) 응답 받기 전 crash, (3) 응답 받은 직후 분류 전 crash — 세 케이스 모두 IN_PROGRESS row + attempted_items로 보상 가능. release 멱등성이 "실제 차감 없는 item"에 대한 false-positive 보상을 무해화.

### 보조 mitigation

- **idempotent producer + acks=all + retries**: 다수 transient 실패는 KafkaTemplate 레벨에서 자동 회복. 컨슈머(inventory-service) 측은 (orderId, productId) 멱등 키로 중복 처리 무해.
- **메트릭/알람**: `compensation_dispatch_failed_total` 카운터를 `/actuator/prometheus`로 노출. 운영 환경에서 alert rule 후크 포인트.

### 채택하지 않은 더 강한 mitigation

- **outbox 패턴 풀스택 도입**: SQLite 환경에서 polling outbox만 가능하고 ROI 부족 (위 "근거" 절 참조). `pending_compensations`가 사실상 boundary 제한 polling outbox로, 보상 토픽 한 곳에만 적용해 표면을 제한.

### 추가로 적용한 mitigation (구현됨)

- **백그라운드 sweeper (`@Scheduled`)**: 부팅 시 회복만으로는 앱 재시작 없이 Kafka가 복구되는 운영 시나리오에서 `DISPATCH_FAILED` row가 다음 재기동까지 남는 한계가 있음 → `CompensationRecoveryRunner.scheduledSweep()`을 30초 주기(`order.compensation.sweeper-interval-ms` 조정)로 추가. `recover()` 메서드는 ApplicationRunner와 동일 코드를 공유.

> **남는 윈도우**: (a) 첫 IN_PROGRESS INSERT 자체가 실패하는 경우 — reserve가 아직 시작 안 됐으므로 재고 leak 없음. (b) Kafka 발행 자체의 catastrophic 실패 — KafkaTemplate retries로 대다수 transient 실패 회복. (c) 이론적으로 attempted_items append commit ↔ HTTP 호출 시작 사이의 매우 좁은 윈도우 — commit이 먼저이므로 이 윈도우에서 crash 시 attempted에 item이 있고 실제 차감은 없음 → recovery에서 release 발행되어도 멱등 no-op. 결론: 다중 item 부분 성공 시나리오의 재고 leak 위험은 본 mitigation으로 운영 수준에 근접하게 닫힘. catastrophic Kafka 실패만 outbox 풀스택이 아닌 한 미보장.

### Confirmed 이벤트 발행 실패 정책

`order.order-confirmed.v1`은 fan-out이라 본 과제에 컨슈머가 부재. 발행 실패 시 정책:

- **사용자 응답**: 201 그대로 반환. 주문은 이미 CONFIRMED 상태로 persist 완료, 클라이언트 입장에서는 주문 생성 성공.
- **pending_compensations 영향 없음**: COMPLETED 마킹은 이미 Order persist 트랜잭션에서 커밋됨. confirmed 발행 실패가 보상 복구를 트리거하지 않음 — 이미 확정된 주문의 재고를 부당 release하지 않도록 설계된 핵심 보장.
- **로그/메트릭**: ERROR 로그 + `confirmed_dispatch_failed_total` 메트릭 증가.
- **재시도/복구**: 본 과제 범위에서는 추가 mitigation 없음. 운영 환경에서 컨슈머가 추가되면 fan-out도 outbox로 전환 검토.
- **근거**: 이 이벤트는 보상 트리거가 아니므로 손실이 재고/주문 invariant를 깨지 않음. 사용자 응답을 5xx로 끊으면 "주문은 만들어졌는데 사용자에겐 실패로 보임" 일관성 깨짐이 더 큼.

## 검토한 대안

### Polling outbox publisher (`@Scheduled` + outbox 테이블)
- SQLite 환경에서 가능한 유일한 outbox 구현.
- 구성: `outbox_events(id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at)` 테이블 + 트랜잭션 INSERT + 주기적 폴링 발행자 + `published_at` 마킹.
- 기각 사유: 위 "근거" 절에 명시. polling 변형은 outbox의 robustness 가치를 충분히 회수하지 못하면서 코드 표면·운영 항목만 추가.

### pg_notify trigger + listener + sweeper (PostgreSQL 환경)
- 이전 프로젝트에서 채택해 운영 경험이 있는 패턴. **당시 회사 인프라 정책상 Kafka 도입이 불가하여, 가용한 PostgreSQL 스택만으로 메시징 발행을 구축해야 하는 제약 조건**이었음. outbox 테이블 INSERT 시 트리거가 `pg_notify`를 발사 → 별도 리스너 프로세스가 `LISTEN`으로 저지연 수신·발행 → 주기적 sweeper가 LISTEN 연결 단절 등으로 누락된 레코드 회수. 즉시성과 robustness를 동시에 확보하는 구성.
- 본 과제 환경 적용 가능성: Kafka가 가용하므로 destination이 다르지만, "트리거 + 리스너 + 스위퍼"의 outbox 구조 자체는 동일하게 이전 적용 가능. 다만 **SQLite에는 `pg_notify` 대응 기능이 부재**하여 직접 이전 불가.
- 운영 환경 전환 시(DB가 PostgreSQL로 변경) ADR을 재작성하여 이 패턴 채택 1순위.

### Debezium (log-based CDC)
- 폴링 부하 0, 마이크로초 단위 latency, 자연스러운 순서 보장.
- 기각 사유:
  - 본 과제 스택의 SQLite는 Debezium connector 미지원. PostgreSQL/MySQL이었다면 검토할 1순위 옵션.
  - Kafka Connect 클러스터 운영 부담이 본 과제의 "외부 의존 없는 Embedded Kafka" 원칙(README "자주 묻는 질문" 절의 docker-compose 항목)과 충돌.
- 운영 환경 전환 시(DB가 MySQL/PostgreSQL) outbox + Debezium 조합으로 재구성 검토.

### `@TransactionalEventListener(phase = AFTER_COMMIT)` 기반 in-JVM hook
- Spring의 트랜잭션 동기화로 커밋 직후 콜백 실행. 발행 실패 시 outbox로 fallback하는 변형도 가능.
- 기각 사유:
  - 같은 JVM 내 hook이라 프로세스 다운 시 이벤트 영구 손실. polling outbox보다 약함.
  - 다중 인스턴스에서 의미 없음. 본 과제는 단일 인스턴스라 직접 영향은 없으나, "이 결정의 한계가 인스턴스 수에 비례한다"는 비대칭이 ADR로 설명하기 부담.

## 검증과 한계

- **검증**:
  - KafkaTemplate producer 설정(`acks=all`, `enable.idempotence=true`)이 application.yml에 적용된 상태로 정의 — 일시적 발행 실패 시 broker가 producer-level retry로 흡수. 본 과제 범위에서 broker 장애 주입까지는 수행하지 않음.
  - 컨슈머 측 멱등 검증: 동일 `(orderId, productId)` 키로 release 이벤트가 두 번 도착해도 한 번만 효과 적용 — `Reservation.release()`의 RELEASED 재호출 no-op과 reservations UNIQUE 제약이 받쳐줌. inventory listener 통합 테스트에서 reserve→release→release 시나리오로 커버.
  - **pending_compensations 라이프사이클**: 다중 item 부분 실패 시 IN_PROGRESS → attempted_items 점진 append → 분류(explicit 시 제거 / ambiguous 시 보존) → READY_TO_PUBLISH → PUBLISHED 전이 검증. 발행 단계 강제 실패 시 DISPATCH_FAILED 전이 + 메트릭 증가 + 재기동 후 자동 재발행 검증.
  - **reserve 중 crash 회복**: HTTP 호출 직전 attempted_items append → 응답 전 강제 다운 → 재기동 시 IN_PROGRESS row + attempted_items 발견 → CRASH_RECOVERY로 표시 후 보상 발행. release가 (실제 차감 안 된 item에 대해) no-op로 처리되는지 확인.
  - **명시적 4xx에서 attempted_items 제거 검증**: i번째에서 explicit 4xx → items[i]가 attempted_items에서 제거됨 → 보상에 포함되지 않음.
  - **confirmed 발행 실패 시 보상 복구 미트리거**: Order persist 성공 후 KafkaTemplate에 confirmed 발행 강제 실패 주입 → 사용자 응답 201 + Order CONFIRMED 상태 persist 확인 + `pending_compensations.status=COMPLETED` 확인 + 컨텍스트 재기동 후 복구 스캔이 이 row를 건드리지 않음 (release 미발행) 확인. **이 케이스가 핵심 race를 막는지 회귀 테스트로 보존**.
  - **메트릭 증가**: confirmed/compensation 발행 실패 시 각각 `order.saga.confirmed_dispatch_failed`, `order.saga.compensation_dispatch_failed` Micrometer 카운터 증가. 통합 테스트에서 발행 실패 주입 후 카운터 증가 검증.
- **한계 (의도적으로 수용한 트레이드오프)**:
  - **첫 IN_PROGRESS INSERT 실패 윈도우**: 이 시점에는 reserve 호출이 시작되지 않아 재고 차감이 없으므로 재고 leak invariant 유지. 다만 사용자 응답이 5xx로 끊기는 점은 보장 안 됨.
  - **Fan-out 이벤트(`order.order-confirmed.v1`)는 mitigation 없음**: 본 과제 컨슈머 부재라 회귀 표면 0. 발행 실패 시 사용자 응답은 201 그대로 (위 정책 절). 운영 환경에서 컨슈머가 추가되면 비용 분석 후 outbox 도입 필요.
  - **pending_compensations row 누적**: COMPLETED·PUBLISHED row의 cleanup 정책은 본 과제 범위 외(운영 시 cron 또는 retention 컬럼). 테스트 후 truncate 정도만 고려.
  - **catastrophic Kafka 실패**: KafkaTemplate retries 한도 초과 시 `DISPATCH_FAILED` 영속화로 재기동 후 재시도 가능하지만, 같은 catastrophic 실패가 지속되면 운영자 개입 필요. 본 mitigation의 boundary.
  - **운영 환경 전환 시 재작성 트리거**:
    - DB가 PostgreSQL → pg_notify trigger + listener + sweeper 기반 outbox로 전환 (본인 운영 경험 보유 패턴).
    - DB가 MySQL/PostgreSQL + Kafka Connect 도입 가능 → Debezium 기반 CDC outbox로 전환.
    - DB가 그대로 SQLite로 production에 사용된다면, 이는 outbox 결정 이전에 DB 선정 자체가 production 부적합이므로 ADR-007 재검토에 앞서 DB 교체 검토.
