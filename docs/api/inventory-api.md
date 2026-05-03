# inventory-service HTTP API

feature/04-inventory-api에서 노출된 실제 contract. 본 문서는 README "호출 예시" 절(과제 원본)을 보완하기 위해 분리해 둔 것이며, 본 구현 시점의 정확한 request/response 형태와 에러 매핑을 빠르게 확인할 수 있도록 한다.

스캐폴드의 curl 예시는 **단일 product 단위 stub** 기준이라 실제 구현의 멱등 키(`orderId`)가 빠져있다. 이 문서의 예시를 사용하면 로컬 스모크 테스트에서 즉시 200/4xx를 받을 수 있다.

## 엔드포인트

### GET `/api/v1/inventory/{productId}` — 재고 조회

```bash
curl -i http://localhost:8082/api/v1/inventory/1001
```

응답 (200):

```json
{ "productId": 1001, "quantity": 50 }
```

### POST `/api/v1/inventory/reserve` — 재고 차감

`(orderId, productId)`가 멱등 키. 동일 키 재호출은 차감 1회만 발생하고 기존 reservation을 그대로 반환한다 (단, quantity가 다르면 409 `RESERVATION_CONFLICT`).

```bash
curl -i -X POST http://localhost:8082/api/v1/inventory/reserve \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1","productId":1001,"quantity":2}'
```

응답 (200):

```json
{ "orderId": "order-1", "productId": 1001, "quantity": 2, "state": "RESERVED" }
```

### POST `/api/v1/inventory/release` — 재고 복원 (보상)

`(orderId, productId)` 멱등 키 기준 release. release-before-reserve race 시 tombstone row를 INSERT 하고 `outcome=TOMBSTONED`를 반환 — 늦게 도착한 reserve는 `409 ALREADY_COMPENSATED`로 거부된다 (ADR-002).

```bash
curl -i -X POST http://localhost:8082/api/v1/inventory/release \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1","productId":1001}'
```

응답 (200):

```json
{ "orderId": "order-1", "productId": 1001, "outcome": "RELEASED" }
```

`outcome`은 `RELEASED` / `ALREADY_RELEASED` / `TOMBSTONED` 중 하나.

## 에러 응답 매핑

| 도메인 사유 | HTTP | `code` |
|---|---|---|
| 존재하지 않는 productId 조회 | 404 | `PRODUCT_NOT_FOUND` |
| 재고 부족 또는 product 미존재 (reserve) | 409 | `INSUFFICIENT_STOCK` |
| tombstone에 의해 차단된 늦은 reserve | 409 | `ALREADY_COMPENSATED` |
| 동일 멱등 키 + quantity 불일치 (payload drift) | 409 | `RESERVATION_CONFLICT` |
| 입력 검증 실패 (음수 / 누락 / 0 등) | 400 | `VALIDATION_FAILED` |
| 서비스 경계 검증 실패 (도메인 예외) | 400 | `BAD_REQUEST` |
| JSON 파싱 실패 | 400 | `MALFORMED_REQUEST` |

응답 본문 형태:

```json
{ "code": "INSUFFICIENT_STOCK", "message": "..." }
```

상세는 `inventory-service/.../controller/InventoryExceptionHandler.java`.
