# 브랜치 진행 전략 (계획 vs 실제)

stacked PR 전략으로 진행하며, feature/01에서 잡은 초기 계획에서 일부 브랜치명·범위가 실제 구현 흐름에 맞춰 조정되었다. 본 문서는 그 변경점과 사유를 영구 기록한다 (PR description은 시간이 지나면 추적이 어려워 별도 문서로 분리).

## 진행 표

| # | 초기 계획 | 실제 브랜치 | 상태 | 핵심 산출물 |
|---|---|---|---|---|
| 01 | feature/01-design-notes | feature/01-design-notes | merged | ADR 8건 + README 설계 결정 섹션 |
| 02 | feature/02-domain-model | feature/02-domain-model | merged | Order/OrderItem/Inventory/Reservation 도메인 + 단위 테스트 |
| 03 | feature/03-repository | **feature/03-persistence** | merged | Repository + atomic UPDATE + tombstone + reserve/release service orchestration |
| 04 | feature/04-order-api | **feature/04-inventory-api** | merged | inventory-service controller + DTO + RestControllerAdvice |
| 05 | feature/05-feign-resilience | **feature/05-inventory-client** | in progress | order-service의 InventoryClient(Feign + Resilience4j) |
| 06 | feature/06-saga | feature/06-saga (예정) | — | order-service controller + Saga + pending_compensations |
| 07 | feature/07-perf-k6 | feature/07-perf-k6 (예정) | — | k6 시나리오 + ADR-004 수치 확정 |
| 08 | feature/08-docs-finalize | feature/08-docs-finalize (예정) | — | ADR 상태 전환, 문서 정리 |

## 변경 사유

### feature/03-repository → **feature/03-persistence**

작업 진행 중 ADR-002의 "reserve 처리 순서"(claim → 충돌 검사 → atomic conditional UPDATE)가 두 테이블(reservations, inventory)을 단일 트랜잭션으로 묶는 orchestration이라는 점이 명확해졌다. 순수 repository 메서드만으로는 표현이 어렵고, `InventoryReservationService`로 끌어올려야 트랜잭션 경계가 정확해진다.

브랜치명을 좁은 "repository"에서 넓은 "persistence"로 조정한 이유는 (1) JPA 매핑·repository·service 트랜잭션 orchestration까지가 한 묶음이고, (2) 통합 테스트가 service 경계에서 검증되어야 의미가 있기 때문이다.

### feature/04-order-api → **feature/04-inventory-api**

원래 계획은 order-service controller가 먼저였으나, 의존 그래프를 다시 보면 inventory-side HTTP API가 먼저 있어야 자연스럽다:

- order-service controller의 실제 동작은 Saga(feature/06)에 의존
- Saga는 Feign client(feature/05)에 의존
- Feign client는 inventory-side HTTP contract를 호출

→ inventory-side HTTP API가 가장 먼저 박혀있어야 feature/05의 Feign client가 WireMock으로 흉내낼 contract가 정해진다. order controller를 먼저 만들면 downstream이 비어 있어 "받은 요청을 어디로도 못 보내는" 반쪽 PR이 된다.

이 재정렬로 각 PR이 self-contained:
- feature/04: inventory-service가 외부에서 curl로 호출 가능한 완성품
- feature/05: 그 contract를 대상으로 Feign client + Resilience4j 검증
- feature/06: order controller + Saga가 위 두 단계를 통합

### feature/05-feign-resilience → **feature/05-inventory-client**

기능적으로 동일하지만 명명만 조정. `feature/04-inventory-api`(inventory의 inbound 표면) ↔ `feature/05-inventory-client`(order-service의 outbound 표면)의 in/out 페어링이 보기 좋고, 사용 기술(Feign·Resilience4j)이 아닌 역할로 명명되어 향후 라이브러리 교체가 있어도 이름이 stale해지지 않는다. ADR-004는 그대로 본 PR의 핵심 근거 문서.

## 원칙

1. **이름 변경은 PR의 의도가 더 잘 드러나는 경우만**. 단순 취향 변경은 하지 않는다.
2. **변경 시 본 문서를 같이 갱신**. PR 본문에도 사유를 남기되 영구 보관처는 본 문서.
3. **stacked 패턴 유지**. 각 PR은 직전 PR을 base로, GitHub의 base auto-update에 맡긴다.
