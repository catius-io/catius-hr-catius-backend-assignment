# ADR-004: Resilience4j 수치 (서킷 브레이커·타임아웃·재시도)

- **상태**: 잠정 (feature/07 k6 실측 후 확정)
- **결정 (잠정)**: 아래 초기값으로 시작하고, k6 부하 시나리오의 latency 분포·실패율 측정 결과로 보정한다.

| 항목 | 잠정 값 | 비고 |
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

## 근거 (잠정)

- **타임아웃은 SLO 기준으로 역산**: order-service의 P99 응답 시간 SLO를 1500ms로 가정 시, inventory 호출에 1000ms 이내 응답을 요구. 더 길게 잡으면 사용자 경험 악화, 더 짧게 잡으면 healthy한 호출까지 끊을 위험.
- **Retry는 보수적으로 1회만**: reserve는 멱등성이 reservation 테이블의 UNIQUE(order_id)로 보장되어 안전하지만, 과한 재시도는 thundering herd 위험 유발. 1회면 transient network blip 회복에 충분.
- **CB는 빠른 반응 + 빠른 복구 지향**: 작은 sliding window(20)로 장애 감지 latency 단축. half-open 진입까지 5s로 짧게 — Kafka 보상 경로가 있어 sync 호출이 끊겨도 시스템 전체가 마비되지 않음.
- **Slow call threshold가 read timeout보다 짧음**: timeout만 보면 "응답이 늦지만 결국 성공"하는 patterns가 잡히지 않음. 800ms를 slow call로 분류하여 timeout 직전 단계의 degradation을 CB가 인지.

> 이 수치들은 모두 가정 기반이다. **feature/07에서 k6로 실측한 latency 분포·실패율을 보고 보정**한다. 보정 후 본 ADR의 상태를 "확정"으로 전환하고 측정 결과를 "검증" 절에 첨부.

## 검토한 대안 (잠정)

### 더 공격적인 수치 (낮은 timeout, 짧은 sliding window)
- 예: read timeout 500ms, sliding window 10
- 트레이드오프: 빠른 fail-fast로 사용자 응답시간 절감, 그러나 정상 트래픽의 일부도 실패 처리될 위험. 트래픽 특성이 전반적으로 빠른 환경에 적합.

### 더 보수적인 수치 (높은 timeout, 긴 sliding window)
- 예: read timeout 3000ms, sliding window 100, failure rate 70%
- 트레이드오프: 일시적 spike에 둔감해 안정적 운영, 그러나 실제 장애 감지가 늦어져 cascading failure 위험.

### CB 미사용 (단순 retry만)
- 기각 사유: README의 "서비스 간 통신 설계" 항목에 "서킷 브레이커"가 명시되어 있고, sync HTTP 호출 시 callee 장애가 caller에 누적되는 것을 막는 표준 도구. 미사용은 본 과제 핵심 도구 누락.

## 검증과 한계

- **검증 (예정)**:
  - feature/07의 k6 시나리오에서 정상 부하 / inventory 의도적 지연 / inventory 의도적 오류 응답 세 가지를 실측.
  - 각 시나리오에서 CB가 의도된 시점에 open/half-open/closed 전이를 보이는지 `/actuator/circuitbreakers` 로 관찰.
  - WireMock 통합 테스트로도 동일 전이를 결정적으로 재현.
- **한계 (잠정)**:
  - 본 ADR의 수치는 가정 기반. k6 결과로 보정 전까지는 production-grade 근거가 없음.
  - 실제 inventory-service의 latency 특성을 모르고 잡은 값이라, 실측 후 1자릿수 단위로 조정될 가능성 있음.
  - SQLite의 동시 접근 직렬화로 인해 inventory의 latency가 트래픽에 따라 비선형으로 증가할 수 있음 — k6 실측에서 이 패턴이 나타나면 timeout 상향 필요.
