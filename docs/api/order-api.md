# order-service HTTP API

feature/06-saga에서 노출된 실제 contract. `inventory-api.md`와 페어링되며, 본 문서는 실제 contract 기준 curl 예시 + Saga 흐름 요약 + 에러 매핑을 빠르게 확인할 수 있도록 한다.

## 엔드포인트

### POST `/api/v1/orders` — 주문 생성 (Saga 시작점)

```bash
curl -i -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": 1,
    "items": [
      {"productId": 1001, "quantity": 2},
      {"productId": 1002, "quantity": 1}
    ]
  }'
```

성공 응답 (201):

```json
{
  "orderId": "9f3d-uuid-...",
  "customerId": 1,
  "status": "CONFIRMED",
  "items": [
    {"productId": 1001, "quantity": 2},
    {"productId": 1002, "quantity": 1}
  ],
  "createdAt": "2026-05-03T08:00:00Z"
}
```

#### Saga 흐름 (ADR-003)

```
items[] 입력 → 검증 (중복 productId 등) → orderId(UUID) 생성
   ↓
pending_compensations IN_PROGRESS row INSERT
   ↓
for each item:
    attempted_items append (HTTP 호출 직전 commit)
    inventory.reserve 호출 (sync HTTP, Resilience4j)
        explicit 4xx → attempted에서 제거 → fail-fast (분기로)
        ambiguous (5xx/timeout/decode) → attempted 유지 → fail-fast (분기로)
        성공 → 다음 item
   ↓ 전부 성공
[단일 트랜잭션] Order INSERT (status=CONFIRMED) + pending_compensations.status=COMPLETED 커밋
   ↓
order.order-confirmed.v1 발행 (best-effort — 발행 실패 시에도 201 응답)
   ↓
[201 응답]

분기:
- 첫 호출 explicit 4xx                → attempted empty → 보상 발행 안 함, 4xx 응답
- i번째(i≥1) explicit 4xx             → attempted(0..i-1)에 대해 inventory.release-requested.v1 발행, 4xx 응답
- ambiguous (timeout / 5xx / decode)  → attempted(현재 item 포함)에 대해 보상 발행, 503 응답
- reserve 전부 성공 후 Order persist 실패 → attempted 전체에 대해 보상 발행, 5xx 응답
- Order persist 성공 후 confirmed 발행 실패 → 201 응답 (이미 COMPLETED 커밋), 로그/메트릭만 — 보상 복구 미트리거
```

### GET `/api/v1/orders/{orderId}` — 주문 조회

```bash
curl -i http://localhost:8081/api/v1/orders/9f3d-uuid-...
```

응답 (200): 위와 동일한 OrderResponse 형태.

## 에러 응답 매핑

| 도메인 사유 | HTTP | `code` |
|---|---|---|
| 존재하지 않는 orderId 조회 | 404 | `ORDER_NOT_FOUND` |
| inventory의 4xx PRODUCT_NOT_FOUND (Saga 중) | 404 | `PRODUCT_NOT_FOUND` |
| inventory의 4xx INSUFFICIENT_STOCK | 409 | `INSUFFICIENT_STOCK` |
| inventory의 4xx ALREADY_COMPENSATED (tombstone race) | 409 | `ALREADY_COMPENSATED` |
| inventory의 4xx RESERVATION_CONFLICT (payload drift) | 409 | `RESERVATION_CONFLICT` |
| inventory 5xx / timeout / decode 실패 / CB OPEN | 503 | `INVENTORY_UNAVAILABLE` |
| 입력 검증 실패 (`@Valid` 위반) | 400 | `VALIDATION_FAILED` |
| 도메인 입력 불변식 위반 (중복 productId 등) | 400 | `BAD_REQUEST` |
| JSON 파싱 실패 | 400 | `MALFORMED_REQUEST` |
| 경로 파라미터 타입 불일치 | 400 | `BAD_REQUEST` |

응답 본문 형태:

```json
{ "code": "INSUFFICIENT_STOCK", "message": "..." }
```

상세는 `order-service/.../controller/OrderExceptionHandler.java`.

## 입력 불변식 (ADR-001)

- `customerId`: 양수
- `items`: 비어있지 않음, 각 `productId` / `quantity`는 양수
- 같은 `productId` 중복 entry 금지 — 중복 시 즉시 400 (`reservations(order_id, product_id)` UNIQUE 제약과의 충돌 방지)

## 발행되는 Kafka 이벤트 (ADR-005)

| 토픽 | 발행 시점 |
|---|---|
| `order.order-confirmed.v1` | Order persist 트랜잭션 커밋 직후 (best-effort, 실패해도 사용자 응답 201) |
| `inventory.release-requested.v1` | Saga 보상 분기 진입 시 (explicit 부분 실패 / ambiguous / persist failure / 부팅 시 crash recovery) |

`pending_compensations` 테이블이 보상 이벤트 발행의 boundary outbox 역할 — 발행 실패는 `DISPATCH_FAILED`로 영속화 후 부팅 시 재시도 (ADR-007).
