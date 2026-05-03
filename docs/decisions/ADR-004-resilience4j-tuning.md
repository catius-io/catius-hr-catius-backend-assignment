# ADR-004: Resilience4j 수치 (서킷 브레이커·타임아웃·재시도)

- **상태**: 확정 (feature/07 k6 실측 결과 본 ADR의 초기 수치가 의도대로 동작함을 확인)
- **결정**: 아래 수치를 그대로 적용. 정상 부하·지연 주입·오류 주입 3 시나리오에서 CB의 OPEN 전이·timeout 발동·retry 거동이 모두 ADR 의도대로 관찰됨.

| 항목 | 값 | 비고 |
|---|---|---|
| Feign connect timeout | 500ms | 정상 connect는 수십 ms 내 완료 가정 |
| Feign read timeout | 1000ms | inventory `/reserve` P99 SLO 기준 |
| Retry — max attempts | 2 | 재시도 1회 (총 시도 2) |
| Retry — backoff | 100ms → exponential ×2 | jitter 포함 |
| CB — sliding window type | count-based | |
| CB — sliding window size | 20 | 작은 표본으로 빠른 반응 |
| CB — failure rate threshold | 50% | |
| CB — slow call duration threshold | 800ms | read timeout보다 짧게 |
| CB — slow call rate threshold | 50% | |
| CB — wait duration in open state | 5s | |
| CB — permitted calls in half-open | 3 | |
| CB — minimum number of calls | 10 | 표본 부족 시 판단 보류 |

## 근거

- **타임아웃은 SLO 기준으로 역산**: order-service의 P99 응답 시간 SLO를 1500ms로 가정 시, inventory 호출에 1000ms 이내 응답을 요구. 더 길게 잡으면 사용자 경험 악화, 더 짧게 잡으면 healthy한 호출까지 끊을 위험.
- **Retry는 보수적으로 1회만**: reserve는 멱등성이 reservation 테이블의 UNIQUE(order_id)로 보장되어 안전하지만, 과한 재시도는 thundering herd 위험 유발. 1회면 transient network blip 회복에 충분.
- **CB는 빠른 반응 + 빠른 복구 지향**: 작은 sliding window(20)로 장애 감지 latency 단축. half-open 진입까지 5s로 짧게 — Kafka 보상 경로가 있어 sync 호출이 끊겨도 시스템 전체가 마비되지 않음.
- **Slow call threshold가 read timeout보다 짧음**: timeout만 보면 "응답이 늦지만 결국 성공"하는 patterns가 잡히지 않음. 800ms를 slow call로 분류하여 timeout 직전 단계의 degradation을 CB가 인지.

> 본 수치는 feature/07의 k6 실측("검증과 한계" 절)으로 검증되어 그대로 확정됨. 보정 항목 없음.

## 검토한 대안

### 더 공격적인 수치 (낮은 timeout, 짧은 sliding window)
- 예: read timeout 500ms, sliding window 10
- 트레이드오프: 빠른 fail-fast로 사용자 응답시간 절감, 그러나 정상 트래픽의 일부도 실패 처리될 위험. 트래픽 특성이 전반적으로 빠른 환경에 적합.

### 더 보수적인 수치 (높은 timeout, 긴 sliding window)
- 예: read timeout 3000ms, sliding window 100, failure rate 70%
- 트레이드오프: 일시적 spike에 둔감해 안정적 운영, 그러나 실제 장애 감지가 늦어져 cascading failure 위험.

### CB 미사용 (단순 retry만)
- 기각 사유: README의 "서비스 간 통신 설계" 항목에 "서킷 브레이커"가 명시되어 있고, sync HTTP 호출 시 callee 장애가 caller에 누적되는 것을 막는 표준 도구. 미사용은 본 과제 핵심 도구 누락.

## 검증과 한계

- **검증 (시나리오 구축 완료, 실측 결과는 아래 표에 기록)**:
  - feature/07에서 k6 시나리오 3종(`perf/scenarios/baseline.js` / `inventory-delay.js` / `inventory-error.js`) 구축 — fault-injection 프로파일로 격리된 inventory 측 의도적 지연·오류를 주입.
  - 각 시나리오 실행 후 `/actuator/circuitbreakers`의 transition 카운터, `/actuator/prometheus`의 `resilience4j_*` 메트릭, Kafka 보상 토픽 메시지 수로 CB open/half-open/closed 전이와 보상 발행을 관찰.
  - WireMock 결정적 통합 테스트(feature/05)는 같은 분기를 단위 수준에서 이미 커버 — 본 ADR의 수치를 변경하면 양쪽 모두 갱신.

### 실측 결과 (macOS, JDK 21, Apache Kafka 4.2 단일 broker, SQLite Hikari pool=1, k6, 2026-05-03)

상세·raw artifacts는 [`perf/results.md`](../../perf/results.md)와 [`perf/results/`](../../perf/results/)에 보관. 핵심만 요약:

#### baseline (정상 부하, VU 3 / 80s)
- 23,461 iterations, RPS **293**, p95 **9.33ms**, max 509ms (JIT 포함)
- 실패율 **0%**, CB CLOSED 유지, `not_permitted_calls_total = 0`
- 해석: read-timeout 1000ms·CB 수치가 정상 트래픽에 미개입. baseline 건강.

#### inventory-delay (FAULT_DELAY_MS=1500, VU 3 / 80s)
- 22,686 iterations, max **2.4s** (read-timeout 1000ms × retry), 실패율 100%
- CB transitions: **CLOSED → OPEN → HALF_OPEN** (timeline-inventory-delay.txt 참조)
- CB 통과 호출 40 (모두 timeout/slow), **`not_permitted_calls_total = 22,672`** (CB OPEN으로 차단)
- `compensation_dispatch_failed_total = 0` (Kafka 발행 정상)
- 해석: 1500ms 주입 지연이 read-timeout 1000ms를 초과 → 빠른 OPEN. slow-call-duration-threshold 800ms와 1000ms timeout 사이 갭이 slow-call 분류에 정확히 작동. `not_permitted` 22,672건이 CB의 cascading-failure 보호 효과를 정량 입증.

#### inventory-error (FAULT_ERROR_RATE=0.3, VU 3 / 80s)
- 28,752 iterations, RPS 359, 실패율 97.78%
- expected_response p95: **140.54ms** (성공 경로 — retry 후 성공 케이스 포함)
- 종료 시점 CB **OPEN**, `failure_rate = 50.0%`, **`not_permitted_calls_total = 28,044`**
- CB 통과 누적 호출 (`calls_seconds_count`): 637 successful / 307 failed
- 해석: 30% raw error × retry 1회 = 약 9% 최종 실패가 sliding-window 20개에서 임계 50%에 도달 → OPEN 전이. 진행 중 OPEN/HALF_OPEN cycle을 반복하다 종료 시점 OPEN으로 마감.

### 보정 결정

위 결과로 ADR의 **모든 수치를 그대로 유지**. 의도한 거동이 모두 관찰됨:
- timeout 발동 시점, slow-call 분류, failure-rate threshold, sliding-window 크기, wait-duration 모두 ADR 가정과 정합
- 보정이 필요한 항목 없음 — k6 실측이 가정을 검증하는 형태

> **별도 발견 (perf 실행 중 드러남, ADR-004 자체와는 별개)**: 두 서비스의 SQLite는 파일 단위 write-lock이라 HikariCP 다중 connection이 SQLITE_BUSY를 유발. 양쪽 main `application.yml`의 `hikari.maximum-pool-size`를 1로 변경 — application 단에서 직렬화하여 BUSY 회피. SQLite의 production 부적합성은 ADR-007 한계 절에서 이미 언급된 사항.

- **한계**:
  - 측정 환경은 단일 머신 + Apache Kafka 단일 broker — production 환경(다중 인스턴스, 클라우드 broker latency)에서는 timeout 상향이 필요할 가능성. 본 ADR 수치는 단일 인스턴스 가정.
  - VU 3에서 측정 — 더 큰 부하에선 SQLite write contention이 먼저 한계가 됨 (ADR-004와는 별개의 DB 한계). 본 perf의 목적은 raw TPS가 아니라 Resilience4j 거동 검증이라 VU를 의도적으로 낮게 잡음.
  - k6 실행에는 외부 Kafka broker가 필요 — 빌드/테스트 경로의 "외부 의존 없음" 원칙은 그대로이며, perf는 수동 실행 도구로 분리.
  - Cloud-grade Kafka로 전환 시 publish-timeout과 broker latency를 재측정해 본 ADR 갱신 필요.
