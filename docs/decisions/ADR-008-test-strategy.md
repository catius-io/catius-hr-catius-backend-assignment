# ADR-008: 테스트 전략

- **상태**: 확정
- **결정**: 4계층(controller / service / domain / 통합)으로 테스트 책임을 분할한다. 통합 테스트는 `@EmbeddedKafka` + 실제 SQLite + WireMock 조합으로 외부 의존 없이 end-to-end Saga와 동시성 시나리오를 검증한다. E2E(다중 서비스 동시 기동), mutation testing, contract testing은 본 과제 범위에서 도입하지 않는다.

## 근거

### 계층별 테스트 매트릭스

| 계층 | 도구 | 검증 대상 |
|---|---|---|
| **Controller** | `@WebMvcTest` + MockMvc | HTTP 계약(상태 코드·응답 본문·헤더), 예외→HTTP 매핑, 입력 검증 |
| **Service** | JUnit + Mockito (`@Mock`으로 InventoryClient · OrderEventPublisher · OrderRepository 대체) | 비즈니스 분기, Saga 흐름의 분기 로직, 예외 발생 시 보상 호출 여부 |
| **Domain** | pure POJO 테스트 | `Order`·`Inventory` 엔티티의 상태 전이 규칙, 불법 전이 시 예외, 불변식 |
| **통합 (Saga)** | `@SpringBootTest` + `@EmbeddedKafka` + 실제 SQLite + WireMock | 외부 의존 없이 컨트롤러 진입부터 이벤트 발행까지의 end-to-end Saga, 동시성 시나리오, 보상 흐름 |

### 외부 통신 모킹 — 단위는 `@Mock`, 통합은 WireMock

- **단위 테스트의 `InventoryClient` mock**: service 분기 로직만 검증. Feign 설정(타임아웃·재시도)은 검증 대상이 아니므로 가벼움 유지가 우선.
- **통합 테스트의 WireMock**: HTTP 레벨에서 inventory-service를 시뮬레이션. Feign client + Resilience4j(서킷·타임아웃·재시도) 설정이 의도대로 동작하는지를 **실제 HTTP 통신 경로에서 검증**. `@MockBean`으로 client 자체를 mock하면 Resilience4j 설정 검증이 누락됨.
  - 검증 시나리오 예: WireMock에 의도적 지연/오류를 주입하여 서킷 open 진입, half-open 복구, 재시도 후 fallback 경로가 ADR-004에 명시될 수치대로 동작하는지 확인.

### 동시성 시나리오 — `CountDownLatch` + `ExecutorService`

ADR-002의 atomic conditional UPDATE와 ADR-003의 다중 item Saga가 실제로 동작하는지 통합 테스트에서 검증한다. 5가지 핵심 시나리오:

1. **충분 재고 시 동시 reserve N건 모두 성공**
   - product A의 재고 = 1000, 동시에 reserve(qty=1) 요청 100건 (각각 다른 orderId)
   - 기대: 100건 전부 성공, 최종 재고 = 900
   - 검증 의도: race control이 정상 케이스에서 false negative를 만들지 않음

2. **한정 재고 시 정확한 부분 성공**
   - product A의 재고 = 5, 동시에 reserve(qty=1) 요청 20건 (각각 다른 orderId)
   - 기대: 정확히 5건 성공, 15건 InsufficientStockException, 최종 재고 = 0
   - 검증 의도: atomic UPDATE의 `WHERE quantity >= ?` 절이 race 없이 한정 자원을 분배

3. **동일 (orderId, productId) 중복 reserve의 멱등성**
   - 같은 (orderId, productId)로 reserve 2회 호출
   - 기대: 차감은 1회만 발생, 두 번째 호출은 reservation 상태 그대로 반환(또는 정의된 idempotent 응답)
   - 검증 의도: `reservations(order_id, product_id)` UNIQUE 복합 제약 + 도메인 멱등 처리

4. **reserve 성공 → 보상 release → 재고 복원**
   - reserve 성공 후 release 호출, release 2회 호출
   - 기대: 첫 release에서 quantity 복원 + reservation state=RELEASED, 두 번째 release는 no-op
   - 검증 의도: 보상의 멱등성, 상태 전이 정확성

5. **다중 item 주문의 부분 실패 + 보상 (Saga 핵심)**
   - 주문 items = [(productA, 1), (productB, 1), (productC, 1), (productD, 1), (productE, 1)]
   - 사전 재고: A=1, B=1, C=0 (의도적 부족), D=1, E=1
   - 동시에 동일한 items로 같은 product 풀에서 경쟁하는 다른 주문 1건 추가 (orderId 다름)
   - 기대:
     - 두 주문 중 한쪽이 A·B를 먼저 차지한 후 C에서 InsufficientStock fail-fast → A·B에 대한 `inventory.release-requested.v1` 발행 → inventory가 release 처리 → A·B 재고 복원
     - 다른 주문은 (A 또는 B에서) 먼저 InsufficientStock으로 fail-fast하거나, 첫 주문 보상 후 A·B를 차지하고 C에서 fail-fast
     - 두 경로 모두 최종 일관성(invariant: 재고 leak 0, A·B·D·E 재고 = 사전 재고와 동일) 유지
     - 두 주문 모두 4xx 응답, Order persist 없음
   - 검증 의도: 부분 성공 후 Saga 보상이 (1) 정확한 reservedItems 만 release, (2) 동시성 환경에서도 재고 leak 없음, (3) 보상 이벤트의 (orderId, productId) 멱등 키가 동시 트래픽과 충돌하지 않음.

### Saga 통합 테스트의 분기

- **단일 item forward 성공**: POST /orders (items=1개) → reserve 성공 → Order persist → `order.order-confirmed.v1` 발행 → Embedded Kafka 컨슈머가 메시지 수신 확인.
- **다중 item forward 성공**: items=5개 → 5번 sequential reserve 모두 성공 → Order persist → 단일 confirmed 이벤트 발행. WireMock 호출 횟수가 정확히 5회인지 검증.
- **첫 호출 명시적 실패 (재고 부족)**: WireMock이 첫 reserve에 InsufficientStock 응답 → order-service가 4xx 반환 + Order persist 안 됨 + reservedItems가 비어있어 **보상 이벤트 발행 안 됨**.
- **다중 item 부분 실패 (i번째에서 4xx)**: 5개 item 중 3번째 reserve가 InsufficientStock → 1·2번째 reservation에 대한 `inventory.release-requested.v1` **단일 이벤트** 발행 (payload items[]=[item1, item2]) → inventory가 release 처리 → 1·2번째 재고 복원. 3·4·5번째는 호출조차 안 됨(fail-fast 검증).
- **reserve 전부 성공 후 Order persist 실패**: 모든 reserve 성공 → Order persist 단계에서 의도된 예외 발생 → 전체 reservedItems에 대한 보상 이벤트 발행 → inventory가 release 처리 → 재고 복원.
- **Forward 타임아웃 (불명확 실패)**: WireMock에 의도적 지연 → Resilience4j 타임아웃 발동 → 해당 호출의 실제 차감 여부 불명확하므로 idempotent release 보상 발행 (정책 결정, ADR-003 표 참조). 서킷 open 진입까지 트리거하는 시나리오는 별도.
- **보상 이벤트 발행 실패 + 재기동 재시도** (ADR-007 mitigation): KafkaTemplate에 의도적 발행 실패 주입 → `pending_compensations.status=DISPATCH_FAILED` 갱신 + 메트릭 증가 확인 → 컨텍스트 재기동 → 자동 재발행 → inventory가 release 처리.
- **confirmed 이벤트 발행 실패 + 보상 복구 미트리거** (ADR-007 핵심 race 방지): reserve 전부 성공 → Order persist + `pending_compensations.status=COMPLETED` 동일 트랜잭션 커밋 → confirmed 발행을 강제 실패 → 사용자 응답 **201**, Order는 CONFIRMED로 persist, COMPLETED row 확인 → 컨텍스트 재기동 → 복구 스캔이 이 row를 건드리지 않고 release 미발행 → 재고 leak 없음. 이 분기는 confirmed 발행 실패가 정상 확정 주문의 재고를 부당 release하지 않음을 보장하는 회귀 방어선.
- **release-before-reserve race** (ADR-002 tombstone): 두 측면으로 분리 검증.
  - **order-service 통합 테스트 (WireMock)**: WireMock으로 reserve 응답을 의도적 지연 → timeout 발동 → ambiguous 분기에서 release 이벤트가 발행되는지 검증. 여기서 tombstone 자체는 검증 대상 아님 (WireMock은 inventory의 실제 reservation/tombstone 로직을 흉내내지 않음).
  - **inventory-service 통합 테스트 (직접 호출)**: release를 reservation 부재 상태에서 먼저 호출 → tombstone INSERT 확인 → 같은 (orderId, productId)로 reserve 호출 → 차감 거부, `AlreadyCompensated` 응답 + inventory.quantity 불변 검증. 이 분기는 늦게 도착한 reserve가 재고 leak을 만들지 않음을 보장하는 회귀 방어선.
- **중복 productId 입력 거부**: items=[{1001, 1}, {1001, 2}] 입력 → 컨트롤러에서 `IllegalArgumentException` → 400 Bad Request. inventory 호출 없이 즉시 거부.

## 검토한 대안

### E2E 테스트 (TestContainers + 다중 서비스 동시 부트)
- 기각 사유: order-service와 inventory-service를 동시에 띄워 실제 HTTP·Kafka 통신을 검증하는 형태. 빌드 시간 분 단위 증가, 디버깅 비용 큼. 본 과제 핵심 영역(Saga 견고성, 통신 설계, 동시성)은 단일 서비스 통합 테스트 + WireMock + Embedded Kafka로 충분히 검증 가능.

### Spring Cloud Contract (CDC, contract testing)
- 기각 사유: 컨슈머가 실제로 부재. 발행 측만 있는 환경에서 contract testing은 검증 표면 자체가 없음.

### Mutation testing (Pitest 등) / Coverage threshold (JaCoCo strict)
- 기각 사유: 테스트 품질은 위 시나리오의 의미 있는 케이스 선택으로 증명되며 메트릭 자체가 품질 지표가 아님. 메트릭 추구는 본 과제 시간 가치 대비 ROI 낮음.

### `@MockBean`으로 Feign client 자체 mock (통합 테스트에서)
- 기각 사유: Resilience4j 설정 검증이 누락됨. 서킷 임계치, 타임아웃, 재시도 횟수가 의도대로 동작하는지를 코드 변경 없이 확신할 수 없음. WireMock으로 HTTP 레벨에서 행동을 주입하는 것이 ADR-004의 수치 근거를 검증하는 유일한 방법.

## 검증과 한계

- **검증**:
  - 4계층 모두에 최소 하나 이상의 의미 있는 테스트가 존재.
  - 동시성 5개 시나리오 + Saga 6개 분기 + 보상 발행 실패/재기동 1개 + confirmed 발행 실패/복구 미트리거 1개 + release-before-reserve race 1개 + 중복 productId 입력 거부 1개 시나리오 모두 통합 테스트로 커버.
  - `./gradlew build` 가 외부 의존 없이 통과.
- **한계**:
  - **실제 HTTP·Kafka 행동의 일부 시나리오 미검증**: TCP 패킷 손실, Kafka 브로커 페일오버, 파티션 리밸런싱 등은 WireMock·Embedded Kafka로 재현 불가. 본 과제 범위 밖이라 명시적으로 미커버.
  - **장기 부하 하 메모리/connection pool 누수**: 단발성 테스트로는 검출 어려움. k6 부하 테스트(별도 perf/ 시나리오)에서 일부 보완.
  - **WireMock의 stub 일관성 책임**: 실제 inventory-service의 응답과 WireMock stub이 어긋날 때 검출되지 않음. contract testing 부재의 비용. README "의도적으로 하지 않은 것"에 명시.
