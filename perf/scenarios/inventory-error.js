// inventory에 의도적 5xx 주입 시나리오 — Resilience4j failure-rate CB 전이 + Saga 보상 발행 관찰.
//
// 사전 조건: inventory-service를 fault-injection 프로파일 + error-rate 환경변수로 기동
//   FAULT_ERROR_RATE=0.3 ./gradlew :inventory-service:bootRun \
//     --args='--spring.profiles.active=perf,fault-injection'
//
// 관찰 목표:
//   - inventory가 30% 확률로 500 → AmbiguousInventoryException → retry 1회 후 propagate
//   - failure rate threshold(50%) 도달 → CB OPEN 전이 (sliding-window 기준)
//   - Saga가 매 실패마다 inventory.release-requested.v1 발행 (Kafka)
//   - CB OPEN 동안 503 즉시 응답
//   - wait-duration-in-open-state(5s) 후 half-open → 일부 시도 → 다시 OPEN 또는 CLOSED 복귀
//
// threshold 없음 — 실패율이 의도된 시나리오. /actuator/circuitbreakers와
// inventory-service 로그(release requested), 토픽 메시지로 검증.
//
// 실행:
//   k6 run perf/scenarios/inventory-error.js

import http from 'k6/http';

export const options = {
    stages: [
        { duration: '10s', target: 2 },
        { duration: '60s', target: 3 },
        { duration: '10s', target: 0 },
    ],
};

const BASE = __ENV.ORDER_BASE_URL || 'http://localhost:8081';
const PRODUCT_BASE = 1001;
const PRODUCT_RANGE = 100;

export default function () {
    const productId = PRODUCT_BASE + ((__VU + __ITER) % PRODUCT_RANGE);
    const body = JSON.stringify({
        customerId: __VU + 1,
        items: [{ productId, quantity: 1 }],
    });
    http.post(`${BASE}/api/v1/orders`, body, {
        headers: { 'Content-Type': 'application/json' },
        tags: { kind: 'order' },
    });
}
