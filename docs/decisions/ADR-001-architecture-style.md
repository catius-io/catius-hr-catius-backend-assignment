# ADR-001: 아키텍처 스타일

- **상태**: 확정
- **결정**: 스캐폴드의 3-tier 구조(controller / service / domain / repository)를 유지하되, **외부 의존성(타 서비스 호출, 메시지 발행)에 한해 dependency inversion**을 적용한다. 도메인 모델은 Rich 모델로 작성한다.

## 근거

- **3-tier 채택 사유**: 스캐폴드가 이 구조를 전제하고, README가 명시적으로 controller/service/domain/repository 책임을 안내(스캐폴드 구성 절). 본 과제의 핵심 영역(Saga 견고성·통신 설계·성능·테스트)에 시간을 집중하기 위해 패키지 재배치 비용을 회피.
- **Rich 도메인 모델**: 비즈니스 규칙을 엔티티 메서드로 응집한다. order-service의 `Order`는 단일 상태(CONFIRMED)이므로 상태 전이 메서드 대신 `Order.confirmed(orderId, customerId, items)` 정적 팩토리에 불변식을 둔다 — items 비어있지 않음, 수량 양수, **같은 productId 중복 entry 금지**(중복 시 `IllegalArgumentException` → 컨트롤러에서 400 매핑). 중복 productId를 합산해 정규화하는 대안은 채택하지 않음: (1) 클라이언트 입력 의도를 흐림, (2) reservations 테이블의 `UNIQUE(order_id, product_id)` 멱등성 키와 충돌 가능성, (3) 400으로 명시 거부가 단순. inventory-service의 `Reservation`은 `RESERVED → RELEASED` 전이를 가지므로 `release()` 메서드를 두고 이미 RELEASED 상태에서 재호출 시 **멱등 no-op**(보상 이벤트 재발행을 안전하게 처리하기 위한 전제). `Inventory`의 차감은 atomic conditional UPDATE라 repository 책임이며 엔티티 메서드로 두지 않음. Saga 보상 흐름 검증 시 각 엔티티의 메서드 한 곳을 읽으면 해당 도메인의 invariant가 드러남.
- **Outbound dependency inversion**: 외부 통신은 `InventoryClient`·`OrderEventPublisher` 인터페이스를 service 층에 정의하고 구현체(`FeignInventoryClient`, `KafkaOrderEventPublisher`)는 service 하위 서브패키지에 둔다. 어댑터 후보가 다수(Feign/mock/WireMock)라 interface 비용을 테스트 용이성으로 회수.
- **이름과 실체의 일치**: outbound DI만 도입하므로 "헥사고날"이라는 이름은 과대 진술. layered architecture에 선별적 의존 역전을 적용한 것이라고 정직하게 명명.

## 검토한 대안

### 헥사고날 (Hexagonal / Ports & Adapters) 전면 채택
- 장점: 도메인을 중심에 둔 의존 방향이 코드 레벨에서 강제됨. 어댑터 교체 시 application 코드 무변경.
- 기각 사유:
  - **Inbound port의 비용 대비 효용 부족**. inbound 어댑터는 HTTP 하나뿐이고 MockMvc로 컨트롤러를 직접 테스트하므로 `CreateOrderUseCase` 같은 인터페이스를 추가해도 테스트 표면이 늘지 않음. 클래스·파일 수만 증가.
  - **패키지 재배치 비용**: `adapter/in`, `adapter/out`, `application/port/in`, `application/port/out`, `application/service` 등 8~12개 패키지 신설 필요. 본 과제 분량에서 핵심 영역(Saga·k6·동시성)에 쓸 시간을 잠식.
  - **Repository 이중 추상화 어색함**: Spring Data JPA의 `Repository`가 이미 인터페이스인데 헥사고날 컨벤션에 맞춰 다시 port/adapter로 감싸면 동일 추상화가 두 번 적용됨.

### 순수 3-tier (외부 의존성도 구상에 직접 의존)
- 장점: 코드량 최소.
- 기각 사유:
  - service의 단위 테스트가 인프라(`@SpringBootTest` 또는 `@MockBean`) 의존도가 높아져 빌드 시간·결정성 악화.
  - ADR-007에서 outbox 패턴을 도입하는 시나리오로 전환할 때 service 코드 자체를 수정해야 함. `OrderEventPublisher` 인터페이스를 두면 구현체만 교체.

### Application Service / Domain Service 분리
- 기각 사유: 본 과제에서 여러 애그리거트를 조율하는 로직이 사실상 Saga 하나뿐. 분리 시 패키지·클래스 수만 늘고 두 layer의 책임 경계가 모호해져 도입 가치를 회수하지 못함.

## 검증과 한계

- **검증**:
  - 컨트롤러는 MockMvc로 controller 단위 테스트.
  - service는 `InventoryClient`·`OrderEventPublisher`·`OrderRepository`를 mock하여 단위 테스트 (Saga 분기·예외 매핑 검증).
  - 통합 테스트는 `@EmbeddedKafka` + 실제 SQLite로 end-to-end Saga 흐름.
- **한계**:
  - inbound 어댑터가 추가되는 경우(예: gRPC, message-driven trigger) use case 인터페이스가 없어 컨트롤러·메시지 리스너 양쪽에 service 호출이 중복될 가능성. 본 과제 범위 밖이라 미선반영.
  - Rich 도메인은 JPA 엔티티에 비즈니스 메서드를 두는 형태인데, JPA 프록시·detach 상태에서 메서드 호출 시점 주의 필요. 트랜잭션 경계 안에서만 호출하도록 service에서 보장.
