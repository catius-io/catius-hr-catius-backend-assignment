# k6 실측 결과

ADR-004의 Resilience4j 수치(서킷·타임아웃·재시도)를 검증·확정하기 위한 실측 결과.
3 시나리오 모두 동일한 환경에서 순차 실행되었으며, 각 시나리오 간 두 서비스를 모두 재기동(다른 fault 환경변수 + 깨끗한 DB)했다. 모든 raw 산출물은 [`results/`](results/) 디렉토리에 보관.

## 환경

- **호스트**: macOS, JDK 21 (Temurin), 단일 머신
- **Kafka**: Apache Kafka 4.2.0 (KRaft, 단일 broker, docker)
- **DB**: SQLite (양 서비스 main config: `hikari.maximum-pool-size: 1`로 직렬화)
- **부하 도구**: k6 (3 VU steady, 80s 총 실행)
- **메트릭 폴링**: 2초 주기 `/actuator/prometheus` 스냅샷을 `timeline-*.txt`에 기록
- **실행 시각**: 2026-05-03

## 시나리오별 결과

### 1. baseline (정상 부하)

| 지표 | 값 |
|---|---|
| Total iterations | **23,461** |
| RPS (steady) | **293** |
| p95 / max | **9.33ms** / 509ms (첫 요청 JIT 포함) |
| 실패율 | **0.00%** |
| Threshold | `http_req_failed<5%` ✓, `p95<1500ms` ✓ |
| CB state (종료 시점) | CLOSED |
| CB calls | 23,461 successful / 0 failed |
| `not_permitted_calls_total` | **0** (CB 미개입) |

**해석**: read-timeout 1000ms는 정상 트래픽에 충분한 여유. Saga end-to-end가 한 자릿수 ms로 완료. CB·retry가 한 번도 개입하지 않은 건강한 baseline.

Raw: [`baseline.txt`](results/baseline.txt) · [`baseline.json`](results/baseline.json) · [`metrics-after-baseline.txt`](results/metrics-after-baseline.txt) · [`cb-after-baseline.json`](results/cb-after-baseline.json) · [`timeline-baseline.txt`](results/timeline-baseline.txt)

### 2. inventory-delay (FAULT_DELAY_MS=1500)

| 지표 | 값 |
|---|---|
| Total iterations | **22,686** |
| RPS | **281** |
| p95 / max | 7.53ms (대부분 fast-fail이라 짧음) / **2.4s** (read-timeout × retry) |
| 실패율 | 100% (의도된 결과) |
| CB state (종료 시점) | **HALF_OPEN** |
| CB 통과 누적 호출 (`calls_seconds_count`) | 0 successful / 40 failed |
| 종료 시점 sliding window (buffered) | 0 successful / 2 failed |
| **`not_permitted_calls_total`** | **22,672** ← CB OPEN으로 차단된 호출 수 |
| `compensation_dispatch_failed_total` | 0 |

**해석**:
- 전체 22,686 호출 중 단 40개만 sliding-window를 통과해 CB가 평가 — 나머지 22,672개는 CB가 OPEN인 상태에서 fast-fail (`not_permitted_calls`로 카운트)
- 통과한 40개는 모두 read-timeout 1000ms로 실패 → `minimum-number-of-calls=10` + 100% failure rate → 즉시 OPEN 전이
- timeline-inventory-delay.txt에서 CLOSED → OPEN → HALF_OPEN 전이 시점 직접 확인 가능
- max 2.4s = 1500ms 지연 × retry 1회 시도 후 timeout 누적

Raw: [`inventory-delay.txt`](results/inventory-delay.txt) · [`inventory-delay.json`](results/inventory-delay.json) · [`metrics-after-inventory-delay.txt`](results/metrics-after-inventory-delay.txt) · [`cb-after-inventory-delay.json`](results/cb-after-inventory-delay.json) · [`timeline-inventory-delay.txt`](results/timeline-inventory-delay.txt)

### 3. inventory-error (FAULT_ERROR_RATE=0.3)

| 지표 | 값 |
|---|---|
| Total iterations | **28,752** |
| RPS | **359** |
| p95 (전체) / p95 (`expected_response:true`) | 7.02ms / **140.54ms** (성공 경로 — retry 후 성공 케이스 포함) |
| max | 458ms |
| 실패율 | 97.78% (CB OPEN 구간 + 의도된 5xx) |
| CB state (종료 시점) | **OPEN** |
| CB 통과 누적 호출 (`calls_seconds_count`) | 637 successful / 307 failed |
| 종료 시점 sliding window (buffered) | 10 successful / 10 failed |
| `failure_rate` (현재 window) | 50.0% (임계 도달) |
| **`not_permitted_calls_total`** | **28,044** ← CB OPEN으로 차단 |
| `compensation_dispatch_failed_total` | 0 |

**해석** (실행 중 거동 vs 종료 시점):
- 시나리오 진행 중 CB가 CLOSED → OPEN → HALF_OPEN cycle을 여러 차례 반복 (timeline-inventory-error.txt에서 직접 확인 가능)
- 종료 시점 OPEN — 마지막 sliding-window의 failure rate가 임계 50%에 도달한 직후 종료
- not_permitted 28,044건이 CB OPEN의 cascading-failure 보호 효과를 정량 입증 — 이 호출들이 inventory에 도달했다면 추가 부하 + retry overhead로 system이 더 악화됐을 것

Raw: [`inventory-error.txt`](results/inventory-error.txt) · [`inventory-error.json`](results/inventory-error.json) · [`metrics-after-inventory-error.txt`](results/metrics-after-inventory-error.txt) · [`cb-after-inventory-error.json`](results/cb-after-inventory-error.json) · [`timeline-inventory-error.txt`](results/timeline-inventory-error.txt)

## ADR-004 수치 보정 결정

위 3 시나리오 결과로 ADR의 **모든 수치를 그대로 유지**:

| 항목 | 값 | 검증 근거 |
|---|---|---|
| Feign read-timeout | 1000ms | inventory-delay에서 의도된 timeout 트리거 (max 2.4s = 1500ms × retry) ✓ |
| Retry max-attempts | 2 | inventory-error의 expected_response p95 140ms = retry 후 성공 ✓ |
| CB failure-rate-threshold | 50% | error 시나리오에서 OPEN 전이 (final state OPEN, failure_rate 50.0%) ✓ |
| CB slow-call-duration-threshold | 800ms | delay 시나리오에서 slow-call 분류 (slow_calls failed 2 in buffered) ✓ |
| CB wait-duration-in-open | 5s | timeline에서 OPEN/HALF_OPEN 주기 관찰 ✓ |
| CB sliding-window-size | 20 | 빠른 OPEN 전이에 충분한 표본 (delay 시나리오에서 통과 누적 40 calls만으로 OPEN) ✓ |
| CB minimum-number-of-calls | 10 | baseline에선 OPEN 미전이 / `not_permitted=0`, fault 시 신속 개입 ✓ |

ADR-004 상태: **잠정 → 확정**. 보정 필요 항목 없음 — k6 실측이 가정을 검증.

## 별도 발견 (ADR-004와 별개)

두 서비스의 SQLite는 파일 단위 write-lock이라 HikariCP 다중 connection이 SQLITE_BUSY를 유발. 첫 시도(VU=10, pool=10)에서 75% 실패율로 드러남 → 양쪽 main `application.yml`의 `hikari.maximum-pool-size: 1`로 변경하여 application 단에서 직렬화. 이후 baseline 0% 실패율 달성.

SQLite의 production 부적합성은 ADR-007 한계 절에서 이미 언급된 사항이며, 본 perf 실행 중 명시적으로 드러난 형태로 추가 검증됨.
