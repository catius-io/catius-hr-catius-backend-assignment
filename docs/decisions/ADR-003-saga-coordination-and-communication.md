# ADR-003: Saga 조정 및 통신 모델

- **상태**: 확정
- **결정**: 두 축을 함께 결정한다. **Saga 조정 = Orchestration** (order-service가 중앙 조정자). **통신 채널 = 분할**:
  - Forward(재고 예약): sync HTTP (Feign + Resilience4j). order는 items[]를 받아 **각 item별 inventory.reserve를 sequential 호출**, 첫 실패 시 fail-fast.
  - Backward(보상): async Kafka 이벤트. 부분 성공한 reservation들에 대해 `inventory.release-requested.v1` 단일 이벤트 발행 (payload에 items[] 포함).
  - Fan-out(주문 확정 후속): async Kafka 이벤트.

> 두 축은 직교한다(Orchestration이 곧 sync, Choreography가 곧 async가 아님). 본 ADR은 두 결정을 묶어서 다룬다. 분리해 별도 ADR로 두면 동일 트레이드오프(가시성·범위 정렬·운영 비용)가 양쪽에 중복 등장해 일관성이 깨지기 쉬움.

## 근거

### Saga 조정 = Orchestration
- **보상 판단·orchestration 응집**: "어느 단계에서 실패했는지, 어떤 보상이 필요한지" 결정 로직이 order-service 한 곳에 모임. (Saga의 모든 상태가 한 DB에 있다는 의미는 아님 — 실패 주문은 Order에 persist되지 않고, 진행 중 상태는 `pending_compensations`에 부분적으로 존재.) 디버깅·모니터링·장애 대응 시 의사결정 코드를 한 코드베이스에서 추적 가능.
- **보상 로직 응집**: Saga 보상 로직이 한 곳에 모여 견고성을 직접 검증할 코드가 단일 코드베이스에 존재. Choreography였다면 보상이 각 서비스의 이벤트 핸들러에 흩어져 검증 표면이 분산.
- **본 과제 분량 적합**: 단일 orchestrator + 두 개의 참여자(self + inventory)는 가장 단순한 형태의 Saga. 추가 추상화(이벤트 코레오그래피 미들웨어, 분산 상태 관리)가 분량 대비 가치를 회수하지 못함.

### 통신 채널 분할
- **Forward sync**: 스캐폴드의 API 계약(`POST /api/v1/orders → 201`)이 즉시 응답을 전제. 서킷 브레이커·타임아웃·재시도를 한 곳(order-service)에서 관측·제어할 수 있어 invariant-critical 단계에 적합.
- **Backward async**: 보상은 eventual success 보장이 본질. Kafka의 retry/DLQ 메커니즘이 sync 재시도보다 견고하며, 호출자(order-service)에 재시도 책임이 누적되지 않음.
- **Fan-out async**: 다수 컨슈머와의 결합도 분리. 발행자가 컨슈머를 모르는 것이 도메인 이벤트의 핵심 가치이며, 후속 확장(알림·정산·분석)을 코드 변경 없이 수용.

### 다중 item 주문의 forward 흐름

scaffold의 inventory `/reserve` API가 **단일 product 단위**(README "호출 예시" 절의 inventory 예시 `{"productId":1001,"quantity":2}`)이고 order는 **items[]** 배열을 받는다(같은 절의 order 예시). 따라서 다중 item 분해와 부분 실패 보상은 **order-service Saga의 책임**이다.

**Forward 알고리즘 (sequential fail-fast)**: 실패는 두 종류로 분기한다 — **명시적 실패**(4xx: 재고 부족·product 미존재 등 호출이 거부되어 차감이 없었음이 확정)와 **불명확 실패**(timeout / 5xx / connection failure: 차감 발생 여부 확신 불가).

`attempted_items`는 `pending_compensations` 테이블에 영속화된다. HTTP 호출 직전에 append, 응답 분류 후 갱신. 상세 영속화 메커니즘은 [ADR-007](ADR-007-event-publishing-and-transactional-consistency.md).

```
items[] 입력 (N개)
INSERT pending_compensations(order_id, attempted_items=[], status=IN_PROGRESS)
for i in 0..N-1:
    attempted_items.append(items[i])  // HTTP 호출 직전 commit
    try:
        inventory.reserve(orderId, items[i].productId, items[i].quantity)
        // 성공 → 다음 i+1로
    catch ExplicitFailure (4xx with semantic body):
        attempted_items.remove(items[i])  // 차감 없음 확정 → 보상 대상에서 제거
        if attempted_items is not empty:
            status=READY_TO_PUBLISH, reason=EXPLICIT_FAILURE
            publish inventory.release-requested.v1 { orderId, items: attempted_items }
        else:
            status=COMPLETED  // 보상할 자원 없음
        return 4xx to client
    catch AmbiguousFailure (timeout / 5xx / connection failure):
        // 차감 여부 불명확 → attempted에 보존
        status=READY_TO_PUBLISH, reason=AMBIGUOUS_FAILURE
        publish inventory.release-requested.v1 { orderId, items: attempted_items }
        return 5xx to client

// 전부 성공
[단일 트랜잭션]
  Order INSERT (status=CONFIRMED)
  pending_compensations.status = COMPLETED
  COMMIT
publish order.order-confirmed.v1  // 발행 실패해도 보상 미트리거
return 201

// reserve 전부 성공 후 Order INSERT 실패 시 (위 트랜잭션 롤백):
status=READY_TO_PUBLISH, reason=PERSIST_FAILURE
publish inventory.release-requested.v1 { orderId, items: attempted_items }
return 5xx to client
```

inventory 측에서 release 처리 시 두 가지 케이스에 대비:
- **정상 케이스**: reservation row(state=RESERVED) 발견 → state=RELEASED 갱신 + quantity 복원
- **release-before-reserve race**: reservation row 부재 (timeout 직후 발행된 release가 늦게 도착하는 reserve보다 먼저 도착) → **tombstone INSERT**(state=RELEASED, quantity=0). 이후 늦게 도착한 reserve가 UNIQUE 충돌 + state=RELEASED 보고 차감 거부 (`AlreadyCompensated`). 상세는 [ADR-002](ADR-002-concurrency-strategy.md).

**Sequential 채택 사유**: parallel 호출 시 latency는 단축되나 부분 성공 처리(어느 호출이 성공했는지 join 후 판단), 동일 order의 동시 reservation row 삽입에서 트랜잭션 격리 의존성, 보상 이벤트 발행 타이밍 — 세 축 모두 코드·테스트 표면을 늘림. N이 작은 본 과제 범위에서는 sequential의 단순성이 우월.

**부분 성공 추적**: 위 의사코드의 `reservedItems`는 **개념적 변수**이며 실제 영속화는 `pending_compensations` 테이블의 `attempted_items_json` 컬럼이 담당한다. 핵심:
- **HTTP 호출 직전**에 attempted_items에 append (응답 전 crash에 대비)
- explicit 4xx 응답 시 attempted에서 해당 item 제거 (차감 없음 확정)
- ambiguous 응답 시 attempted에 보존 (차감 여부 불명확 → at-least-once 보상)
- crash 후 recovery 시 attempted 전체를 ambiguous로 간주

상세 스키마·라이프사이클·재기동 복구는 [ADR-007](ADR-007-event-publishing-and-transactional-consistency.md).

**보상 이벤트 멱등성**: 동일 `(orderId, productId)`에 대한 release는 inventory 측에서 멱등 (이미 RELEASED 상태면 no-op). ADR-002의 `UNIQUE(order_id, product_id)` 제약이 이를 자연스럽게 처리.

## 검토한 대안

### inventory `/reserve`를 items[] 배열로 받는 all-or-nothing 단일 호출 (B-lite)
- 구성: order-service가 items[]를 그대로 inventory에 전달, inventory가 단일 트랜잭션 내에서 N개 atomic UPDATE를 수행하고 하나라도 실패하면 rollback. 보상이 사실상 불필요.
- 장점: order-service Saga 흐름이 단일 boundary로 단순화, 테스트 매트릭스 축소, latency 단축.
- 기각 사유:
  - **scaffold contract 위반**: README "호출 예시" 절의 inventory `/reserve`가 단일 product 형태로 명시되어 있어 이를 items[] 받는 형태로 변경하면 scaffold 컨트랙트 임의 수정. scaffold가 단일 product API를 전제하는 것으로 해석됨.
  - **Saga 보상 견고성 입증 표면 축소**: 부분 실패→보상 흐름이 inventory 트랜잭션 내부로 흡수되어, Saga 보상 로직의 견고성을 입증할 코드 표면이 사라짐. 본 과제의 핵심 영역 중 하나가 비어버리는 트레이드오프.
  - 운영 환경에서 다중 item을 한 트랜잭션으로 묶어야 한다는 요구가 강하면 재검토 가치 있으나, 본 과제 맥락에서는 위 두 사유로 부적합.

### Orchestration + 전부 Async (command/reply 토픽 기반)
실무 운영 환경에서 더 선호될 수 있는 모델. 장점:
- **Temporal decoupling**: inventory-service 일시 다운 시에도 주문을 accepted 상태로 흡수하는 graceful degradation 가능.
- **백프레셔 자연 흡수**: 트래픽 스파이크 시 큐가 버퍼 역할. sync는 thread pool 고갈 시 cascading failure 위험.
- **장애 격리**: 컨슈머 측 장애가 발행자에 직접 전파되지 않음.
- **독립 스케일**: 컨슈머를 큐 깊이 기반으로 발행자와 무관하게 스케일.

본 과제에서 채택하지 않은 사유:
- 스캐폴드의 API 계약(즉시 201 응답)이 sync 응답을 전제. async로 가면 `202 Accepted` + 폴링 모델로 계약 자체를 변경해야 함.
- 기술 스택이 OpenFeign + Resilience4j로 고정. forward를 Kafka로 빼면 이 도구들이 적용될 표면이 사라져 서비스 간 통신 설계 결정의 적용 영역이 비어버림.
- async orchestration의 추가 운영 비용(reply 토픽, correlation ID, orchestrator 측 타임아웃, 컨슈머 측 멱등성)이 본 과제 범위에서 검증할 보상 견고성 가치를 압도. 비용 대비 효용이 안 맞음.

### Choreography (이벤트 기반, 중앙 조정자 부재)
- Saga 진행 상태가 각 서비스의 이벤트 핸들러에 분산되어, "지금 이 주문이 어느 단계인가?" 질문에 대한 단일 답이 없음. 가시성·디버깅·운영 비용 큼.
- 보상 흐름이 다단계로 사슬을 이루면 전이 매트릭스가 폭발적으로 늘어남. 본 과제 분량에서 검증 표면이 통제 불가.
- 장점은 명확함(서비스 간 결합도 최소, 새 참여자 추가가 발행자 수정 없이 가능). 다만 본 과제는 참여자가 둘뿐이고 결합도 절감의 가치가 작음.
- 통신 채널과 직교한 결정이라 "Choreography + sync HTTP"라는 조합도 이론상 가능하지만, 실무에서 거의 채택되지 않으며 본 과제 맥락에서도 매력적인 후보가 아님.

## 검증과 한계
- **검증** (`@EmbeddedKafka` + WireMock 통합 테스트):
  - **단일 item forward 성공**: 1개 item 주문 → 1번 reserve → Order persist → `order.order-confirmed.v1` 발행.
  - **다중 item forward 성공**: N개 item 주문 → N번 sequential reserve → 전부 성공 → Order persist → 단일 confirmed 이벤트.
  - **다중 item 부분 실패 + 보상**: 5개 item 중 3번째에서 InsufficientStock → 1·2번째 reservation에 대한 `inventory.release-requested.v1` 단일 이벤트 발행 (payload items[] = [item1, item2]) → inventory가 release 처리하여 재고 복원.
  - **명시적 4xx — 첫 호출**: 첫 reserve가 4xx → reservedItems 비어있음 → 보상 발행 안 함, 4xx 응답.
  - **명시적 4xx — i번째(i≥1)**: 5개 item 중 3번째에서 4xx → 1·2번째에 대한 보상 발행, 3번째는 보상 대상 아님.
  - **불명확 실패 — 첫 호출 timeout**: WireMock 의도적 지연 → 첫 호출 timeout → reservedItems는 비었지만 **현재 item을 포함한 보상 발행**(items[]=[item1]) → inventory에서 release no-op (실제 차감 없었음) 또는 정상 release (실제 차감 있었음).
  - **불명확 실패 — i번째 timeout**: 5개 item 중 3번째에서 timeout → 1·2번째 + 3번째 포함 보상 발행 (items[]=[item1, item2, item3]).
  - **보상 이벤트 멱등성**: 동일 (orderId, productId) release 2회 수신 시 한 번만 효과 적용.
  - **release-before-reserve race**: 두 측면으로 분리 검증.
    - **order-service 측 (WireMock 통합 테스트)**: WireMock으로 reserve 응답을 의도적 지연 → Resilience4j timeout 발동 → order-service가 ambiguous failure 분기로 진입 → release 이벤트 발행 검증.
    - **inventory-service 측 (직접 통합 테스트, WireMock 없음)**: release를 reservation 부재 상태에서 먼저 호출 → tombstone INSERT 확인 → 그 후 같은 (orderId, productId)로 reserve 호출 → 차감 거부, `AlreadyCompensated` 응답. 재고 변화 없음.

### 실패 유형별 보상 정책

| 실패 유형 | 보상 대상 items | 근거 |
|---|---|---|
| 명시적 4xx (재고 부족·product 미존재) — 첫 호출에서 발생 | 없음 (발행 안 함) | 차감이 없음이 확정 → 보상할 자원 없음 |
| 명시적 4xx — i번째(i≥1)에서 발생 | reservedItems (0..i-1) | 차감이 없는 현재 item은 제외, 이전 성공분만 보상 |
| 불명확 실패 (timeout / 5xx / connection failure) | reservedItems + 현재 item | 차감 여부 불명확 → at-least-once 보상. release는 inventory에서 멱등 처리 |
| reserve 전부 성공 후 Order persist 실패 | reservedItems 전체 | 재고가 차감된 상태에서 Order만 없으면 재고 leak |

- **한계**:
  - **타임아웃 후 늦게 도착한 reserve**: tombstone pattern(ADR-002)으로 차단. 다만 inventory 자체의 reserve·release 두 메시지 **모두** 영구 손실되는 극단 케이스(예: inventory가 reserve 처리 중 다운되어 두 요청 다 유실)는 미커버. README "의도적으로 하지 않은 것"에 명시.
  - **보상 이벤트 발행 자체의 손실**: 발행 직전 프로세스 다운 시 손실 가능. ADR-007의 `pending_compensations` 라이프사이클(IN_PROGRESS → attempted_items 점진 append → 분류 → READY_TO_PUBLISH → 부팅 시 재발행)로 핵심 윈도우는 닫지만 첫 IN_PROGRESS INSERT 자체 실패 윈도우와 catastrophic Kafka 실패는 미보장.
  - **Sequential 호출의 latency 누적**: N개 item 주문은 N×reserve latency. 본 과제 범위에서는 N이 작아 acceptable. 운영 환경에서 큰 N이 빈번하면 parallel 호출 + join 또는 inventory 측 batch API 도입 검토.
