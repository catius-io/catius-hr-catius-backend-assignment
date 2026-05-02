import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─── Custom Metrics ───────────────────────────────────────────
const orderCreated  = new Counter('orders_created');
const orderFailed   = new Counter('orders_failed');
const orderDuration = new Trend('order_create_duration', true);

// ─── SLO Thresholds ───────────────────────────────────────────
//   p(95) < 500 ms  — 95th-percentile 응답 시간
//   failed rate < 1% — HTTP 에러 비율
export const options = {
    stages: [
        { duration: '30s', target: 10 },  // ramp-up: 0 → 10 VUs (워밍업)
        { duration: '30s', target: 30 },  // ramp-up: 10 → 30 VUs
        { duration: '1m',  target: 50 },  // ramp-up: 30 → 50 VUs
        { duration: '2m',  target: 50 },  // steady: 50 VUs 유지 (핵심 측정 구간)
        { duration: '30s', target: 20 },  // ramp-down: 50 → 20
        { duration: '30s', target: 0 },   // ramp-down: 20 → 0
    ],
    thresholds: {
        http_req_duration:       ['p(95)<500'],    // SLO: p95 응답 < 500ms
        http_req_failed:         ['rate<0.01'],    // SLO: 에러율 < 1%
        order_create_duration:   ['p(95)<500'],    // 주문 생성 전용 p95
    },
};

// ─── Configuration ────────────────────────────────────────────
const ORDER_URL     = __ENV.ORDER_URL     || 'http://localhost:8081';
const INVENTORY_URL = __ENV.INVENTORY_URL || 'http://localhost:8082';

const PRODUCT_IDS = [1001, 1002, 1003, 1004, 1005];

const headers = { 'Content-Type': 'application/json' };

// ─── Setup: 헬스체크 + 시드 데이터 확인 ──────────────────────
// 시드 데이터는 inventory-service 의 data.sql 에서 로드됨 (local 프로파일)
export function setup() {
    console.log('=== Setup: verifying services ===');

    // 1) 서비스 헬스체크
    const invHealth = http.get(`${INVENTORY_URL}/actuator/health`);
    const ordHealth = http.get(`${ORDER_URL}/actuator/health`);

    const healthOk = check(null, {
        'inventory-service healthy': () => invHealth.status === 200,
        'order-service healthy':     () => ordHealth.status === 200,
    });

    if (!healthOk) {
        console.error('Services not healthy — aborting.');
        return { healthy: false };
    }

    // 2) 시드 데이터 검증 — productId 1001 재고 확인
    const invRes = http.get(`${INVENTORY_URL}/api/v1/inventory/1001`);
    const seedOk = check(invRes, {
        'seed data loaded': (r) => {
            try { return JSON.parse(r.body).availableQuantity > 0; }
            catch { return false; }
        },
    });

    if (!seedOk) {
        console.error('Seed data not found — start inventory-service with --spring.profiles.active=local');
        return { healthy: false };
    }

    console.log('=== Services healthy, seed data confirmed ===');
    return { healthy: true, products: PRODUCT_IDS };
}

// ─── Default (VU) Function: 주문 생성 시나리오 ───────────────
export default function (data) {
    if (!data.healthy) return;

    // 랜덤 고객 ID, 1~3개 상품을 골라 주문
    const customerId = Math.floor(Math.random() * 10_000) + 1;
    const numItems   = Math.floor(Math.random() * 3) + 1;

    const items = [];
    const usedProducts = new Set();
    for (let i = 0; i < numItems; i++) {
        let pid;
        do {
            pid = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
        } while (usedProducts.has(pid));
        usedProducts.add(pid);
        items.push({
            productId: pid,
            quantity: Math.floor(Math.random() * 5) + 1, // 1~5개
        });
    }

    const payload = JSON.stringify({ customerId, items });
    const start = Date.now();

    const res = http.post(`${ORDER_URL}/api/v1/orders`, payload, { headers });

    const elapsed = Date.now() - start;
    orderDuration.add(elapsed);

    const success = check(res, {
        'status is 200':  (r) => r.status === 200,
        'has order id':   (r) => {
            try { return JSON.parse(r.body).id > 0; }
            catch { return false; }
        },
        'status is CONFIRMED': (r) => {
            try { return JSON.parse(r.body).status === 'CONFIRMED'; }
            catch { return false; }
        },
    });

    if (success) {
        orderCreated.add(1);
    } else {
        orderFailed.add(1);
        if (res.status !== 200) {
            console.warn(`Order failed: HTTP ${res.status} — ${res.body?.substring(0, 200)}`);
        }
    }

    // 각 VU 간 0.5~1.5초 간격 (실 사용자 시뮬레이션)
    sleep(Math.random() + 0.5);
}

// ─── Teardown: 요약 출력 ──────────────────────────────────────
export function teardown(data) {
    console.log('=== Teardown: load test complete ===');
}
