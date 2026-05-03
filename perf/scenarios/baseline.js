// 정상 부하 baseline — POST /api/v1/orders 처리량·지연 측정.
//
// SLO threshold:
//   - http_req_failed: < 5% (정상 트래픽 하 실패율)
//   - p95 응답시간 < 1500ms (ADR-004 가정 SLO)
//   - p99는 정보용, 결과 해석에 활용
//
// 실행:
//   k6 run perf/scenarios/baseline.js
// 또는 base url 오버라이드:
//   ORDER_BASE_URL=http://localhost:8081 k6 run perf/scenarios/baseline.js

import http from 'k6/http';
import { check } from 'k6';

// VU=3 — SQLite 단일 인스턴스의 write 병목을 회피해 CB·timeout·retry 거동에 집중.
// 두 서비스가 같은 SQLite를 공유하지 않더라도 각 서비스의 단일 SQLite write lock이
// HikariCP 다중 connection의 병목이라 큰 VU는 측정 노이즈가 됨.
// 본 시나리오의 목적은 raw TPS가 아니라 정상 부하 하 latency·실패율 baseline 확보.
export const options = {
    stages: [
        { duration: '10s', target: 2 },    // ramp-up
        { duration: '60s', target: 3 },    // steady
        { duration: '10s', target: 0 },    // ramp-down
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{kind:order}': ['p(95)<1500'],
    },
};

const BASE = __ENV.ORDER_BASE_URL || 'http://localhost:8081';
const PRODUCT_RANGE = 100;
const PRODUCT_BASE = 1001;

export default function () {
    // VU/iter 조합으로 productId를 분산 — race를 만들어 atomic UPDATE 동작 관찰.
    const productId = PRODUCT_BASE + ((__VU + __ITER) % PRODUCT_RANGE);
    const body = JSON.stringify({
        customerId: __VU + 1,
        items: [
            { productId, quantity: 1 },
        ],
    });
    const res = http.post(`${BASE}/api/v1/orders`, body, {
        headers: { 'Content-Type': 'application/json' },
        tags: { kind: 'order' },
    });
    check(res, {
        'status 201': (r) => r.status === 201,
    });
}
