# ADR-002: 동시성 전략

- **상태**: 확정
- **결정**: `inventory.quantity` 갱신은 **atomic conditional UPDATE** (`UPDATE ... SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?`)로 race를 회피한다. inventory-service의 `/reserve`·`/release`는 **단일 product 단위** API이며, 다중 item 주문의 분해·부분 실패 보상은 **order-service의 책임**(상세는 ADR-003). 멱등성과 보상 추적은 `reservations` 테이블의 `UNIQUE(order_id, product_id)` 복합 제약으로 보장한다. **release가 reserve보다 먼저 도착하는 경우(ambiguous timeout race) tombstone row를 INSERT**하여 늦게 도착한 reserve가 차감하지 않도록 차단한다. **낙관 락(@Version)도 비관 락(SELECT FOR UPDATE)도 사용하지 않는다.**

## 근거

- **검증·차감 원자화**: "재고가 충분한가"라는 도메인 검증과 "차감"이라는 상태 변경이 한 SQL의 `WHERE quantity >= ?` 절 안에서 함께 일어남. 두 단계로 분리되어 있을 때 발생할 수 있는 race(검증 통과 후 다른 트랜잭션이 차감) 자체가 구조적으로 불가능.
- **락 보유 시간 최소화**: row-level write lock이 UPDATE 실행 순간에만 holding. 비관 락은 트랜잭션 종료까지 holding하므로 처리량 측면에서 불리.
- **데드락 가능성 0**: 락을 획득한 채 다른 락을 기다리는 구조가 없음. 비관 락은 다중 row 차감 시 락 순서 정렬 안 하면 데드락 위험.
- **SQLite 포터빌리티**: 표준 SQL이라 dialect 모호성 없음. SQLite community dialect의 `LockModeType.PESSIMISTIC_WRITE` 매핑 동작을 실측·확신하지 않아도 됨.
- **JPA 의존도 낮음**: `@Modifying` 쿼리 한 줄로 표현 가능. 트랜잭션 격리 수준 의존성이 없음(read-modify-write 패턴이 아니므로).
- **`affected_rows == 0`을 도메인 시그널로**: 영향받은 row 수가 0이면 "재고 부족 또는 product 미존재"로 해석. 별도 검증 코드 불필요.
- **멱등성 책임 분리**: 동시성과 별개로, 같은 (orderId, productId) 조합으로 두 번 들어오는 reserve를 거부해야 하는데 이는 race 문제가 아닌 멱등성 문제. `reservations` 테이블의 `UNIQUE(order_id, product_id)` 복합 제약이 자연스럽게 처리. 다중 item 주문에서 동일 order의 서로 다른 product에 대한 reservation row는 정상적으로 생성되고, 같은 (order, product) 재호출만 차단됨.
- **API 경계 = 단일 product**: inventory-service는 한 product에 대한 atomic UPDATE만 책임진다. order의 items[] 배열을 한 호출로 받아 트랜잭션으로 묶지 않는 이유는 (1) scaffold가 단일 product /reserve 컨트랙트를 명시(README "호출 예시" 절의 inventory 예시), (2) 부분 실패 보상이 Saga의 핵심 표면이라 order-service에서 통제하는 편이 보상 로직 견고성을 더 명확히 드러냄. 단일 product API + order-service의 분해/보상이 본 설계 의도에 정합.
- **Tombstone으로 release-before-reserve race 차단**: ambiguous timeout 시 order-service는 보상 이벤트를 발행하는데, 원래 reserve HTTP 요청은 inventory에 늦게 도착할 수 있다(네트워크 지연 + Kafka 처리 우선). 이 race를 막기 위해 reserve·release 처리 순서를 못 박는다.

### Reserve 처리 순서 (필수)

```
BEGIN TRANSACTION
  -- 1. reservation claim 먼저 (차감 전에 의도 등록)
  INSERT INTO reservations (order_id, product_id, quantity, state)
    VALUES (?, ?, ?, 'RESERVED')
  ON CONFLICT (order_id, product_id) DO NOTHING
  
  -- 2. 충돌 검사
  IF inserted_rows == 0:
      SELECT state FROM reservations WHERE order_id=? AND product_id=?
      IF state == 'RELEASED': ROLLBACK; return AlreadyCompensated  -- tombstone 발견
      IF state == 'RESERVED': ROLLBACK; return AlreadyReserved      -- 멱등 응답
  
  -- 3. claim 성공한 경우에만 atomic conditional UPDATE
  UPDATE inventory SET quantity = quantity - ?
    WHERE product_id = ? AND quantity >= ?
  
  -- 4. 재고 부족 시 reservation도 함께 rollback
  IF affected_rows == 0:
      ROLLBACK; return InsufficientStock
  
  COMMIT; return Reserved
```

**순서가 중요한 이유**: 만약 UPDATE를 먼저 하고 reservation INSERT를 나중에 하면, tombstone이 있어도 늦은 reserve가 재고를 먼저 차감한 뒤에야 UNIQUE 충돌을 만난다. INSERT rollback이 정상 동작하더라도 (1) 의도와 다른 두-단계 흐름이라 race 분석 표면이 늘어나고, (2) 구현 실수 시 `quantity`가 차감된 채 reservation 부재 상태로 빠져 재고 leak. **claim → 충돌 검사 → UPDATE** 순서가 invariant.

### Release 처리 분기

- reservation row 존재 + state=RESERVED → 정상 release (state=RELEASED, quantity 복원)
- reservation row 존재 + state=RELEASED → 멱등 no-op
- **reservation row 부재 → tombstone INSERT (state=RELEASED, quantity=0)**. 이후 같은 (orderId, productId)로 reserve가 도착하면 위 reserve 처리 순서의 충돌 검사 단계에서 차단됨.

## 검토한 대안

### 비관 락 (PESSIMISTIC_WRITE / SELECT FOR UPDATE)
- 기각 사유:
  - SELECT + UPDATE 두 단계로 round-trip이 분리되어 락 보유 시간이 길어짐.
  - SQLite community dialect에서 PESSIMISTIC_WRITE의 매핑이 명확하지 않음. 실제로는 격리 수준에 의존하는 동작일 가능성을 배제하려면 실측 필요.
  - 다중 row 동시 차감 시 데드락 가능성. inventory API가 단일 product 단위라 한 트랜잭션이 단일 row만 잠그지만, 회피 가능한 위험을 감수할 이유 없음.
  - README 체크리스트에 부합한다는 이점은 있으나, 그 한 줄을 위해 위 비용을 받아들일 필요가 없음.

### 낙관 락 (`@Version` 필드 + JPA 자동 감지)
- 기각 사유:
  - 충돌 시 `OptimisticLockingFailureException` 처리 + 재시도 정책 코드가 추가됨. atomic UPDATE는 `affected_rows == 0`만 보면 됨.
  - `version` 컬럼이 추가되어 스키마가 한 줄 늘고, 모든 변경 경로에서 version 증가 책임이 분산됨.
  - 본 과제처럼 "단일 row 차감" 시나리오에는 atomic UPDATE가 정보량·코드량 모두 우월. 낙관 락이 빛나는 경우는 "여러 필드를 읽고 비즈니스 로직 적용 후 갱신" 시나리오.

### TCC (Try-Confirm-Cancel) 정식 채택
- 기각 사유:
  - `/confirm` 엔드포인트 + 두 단계 프로토콜 도입 비용. order-service Saga가 reserve → persist Order → confirm 의 3-step으로 늘어남.
  - 본 과제의 보상 시나리오는 "reserve 성공 후 Order persist 실패 → release"가 사실상 전부. 이 케이스에 명시적 confirm 단계가 추가 가치를 주지 않음.
  - 명시적 confirm 단계가 없는 reservation 패턴(reserve = 사실상 즉시 확정)도 TCC라고 칭할 수는 있으나 정확한 명명이 아님. 정직하게 "reservation pattern + lock-free 동시성 제어"로 명명.

## 검증과 한계

- **검증**:
  - 통합 테스트(`@EmbeddedKafka` + 실제 SQLite): 동일 product에 동시 reserve 요청 N건을 `CountDownLatch` + `ExecutorService`로 발사하고, 한정 재고 K개에 대해 정확히 K건만 성공·나머지 (N-K)건은 InsufficientStockException 응답하는지 확인.
  - 동일 `(orderId, productId)`로 reserve 2회 호출 시 두 번째가 idempotent하게 처리(차감 1회만 발생)되는지 확인.
  - 동일 `orderId`로 서로 다른 product 2개를 reserve 시 두 reservation row가 정상 생성되는지 확인 (다중 item 주문의 정상 케이스).
  - reserve 성공 → release 호출 → quantity 복원, reservation state=RELEASED 전이 확인. release 2회 호출 시 두 번째 idempotent.
  - **release-before-reserve race** (inventory-service 직접 검증, WireMock 없음): 같은 (orderId, productId)에 대해 release를 먼저 호출(reservation row 부재) → tombstone INSERT 확인 → 그 후 reserve 호출 → 위 처리 순서의 충돌 검사에서 차단, `AlreadyCompensated` 응답, inventory.quantity 변화 없음. **재고 부족 시 reservation rollback**도 함께 검증: 재고가 부족한 product에 reserve 호출 → reservation row 미생성(rollback) + InsufficientStock 응답.
- **한계**:
  - **다중 product 부분 실패는 inventory가 아닌 order-service에서 보상**: inventory는 단일 product atomic UPDATE만 책임. 다중 item 주문의 부분 성공 보상은 order-service Saga의 책임이며 ADR-003에서 다룸 (보상 이벤트는 단일 이벤트에 items[]를 담는 형태라 release 순서는 무의미). 한 주문 내 product A는 성공·B는 실패 시 inventory는 두 호출을 독립 사건으로 처리하고, order-service가 A에 대한 release 보상 이벤트를 발행한다.
  - **README 참고 체크리스트 deviation**: README "구현해야 할 것 (요약)" 절의 "낙관/비관 락 중 하나" 항목과 직접적으로 어긋남. 본 ADR이 그 deviation의 명시적 근거.
  - **재고 음수 방어는 SQL 한 줄에 의존**: `WHERE quantity >= ?` 가 사라지면 음수 재고 발생. 코드 리뷰·테스트로 방어. DB 차원의 CHECK 제약(`quantity >= 0`)을 추가하는 것이 더 견고하나, SQLite의 CHECK 제약이 트랜잭션 롤백을 트리거하므로 도입 시 예외 처리 일관성을 별도 설계해야 함 — 본 과제 범위에서는 도입하지 않음.
