// inventory에 의도적 지연 주입 시나리오 — Resilience4j slow-call 감지·CB 전이 관찰.
//
// 사전 조건: inventory-service를 fault-injection 프로파일 + delay 환경변수로 기동
//   FAULT_DELAY_MS=1500 ./gradlew :inventory-service:bootRun \
//     --args='--spring.profiles.active=perf,fault-injection'
//
// 관찰 목표 (threshold가 아닌 해석 대상):
//   - inventory가 1500ms로 응답 → ADR-004의 read-timeout(1000ms) 초과 → AmbiguousInventoryException
//   - slow-call duration threshold(800ms) 기준 slow-call 비율 상승 → CB OPEN 전이
//   - Saga가 ambiguous 분기로 보상 발행 (Kafka에서 inventory.release-requested.v1 관찰)
//   - CB OPEN 동안 후속 호출은 503 즉시 응답 (CallNotPermittedException 매핑)
//
// threshold는 의도적으로 lenient — 실패가 "발생하는 게 정상"인 시나리오라 SLO 단언 부적합.
// 실제 평가는 /actuator/circuitbreakers와 Kafka 토픽 관찰로.
//
// 실행:
//   k6 run perf/scenarios/inventory-delay.js

import http from 'k6/http';

export const options = {
    stages: [
        { duration: '10s', target: 2 },
        { duration: '60s', target: 3 },    // CB minimum-calls=10·sliding-window=20 채우기에 충분
        { duration: '10s', target: 0 },
    ],
    // SLO threshold 없음 — 관찰 목적. http_req_failed는 의도적으로 비활성.
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
    // status 검사 안 함 — 503 / 5xx 발생이 시나리오의 일부.
}
