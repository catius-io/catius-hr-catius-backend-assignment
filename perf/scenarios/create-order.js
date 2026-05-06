/**
 * 주문 생성 부하 테스트
 *
 * 실행:
 *   k6 run perf/scenarios/create-order.js
 *   BASE_URL=http://localhost:8081 k6 run perf/scenarios/create-order.js
 *
 * SLO
 *   - p95 응답시간 < 500ms
 *   - p99 응답시간 < 1000ms
 *   - 에러율 < 1%
 *   - Saga 정상 종료율 > 90% (CONFIRMED 또는 CANCELLED)
 *
 * Request  POST /api/v1/orders
 *   { customerId: Long, items: [{ productId: Long, quantity: int }] }
 * Response 201 Created → List<OrderResponse>
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const orderDuration   = new Trend('order_create_duration', true);
const sagaSuccessRate = new Rate('saga_success_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const INV_URL  = __ENV.INV_URL  || 'http://localhost:8082';

const PRODUCT_IDS  = [1001, 1002, 1003, 1004, 1005];
const CUSTOMER_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

// ── SLO thresholds ────────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // ramp-up
    { duration: '60s', target: 10 },  // steady-state
    { duration: '20s', target: 0  },  // ramp-down
  ],
  thresholds: {
    http_req_duration:     ['p(95)<500', 'p(99)<1000'],
    http_req_failed:       ['rate<0.01'],
    order_create_duration: ['p(95)<500'],
    saga_success_rate:     ['rate>0.90'],
  },
};

// ── 테스트 데이터 준비 (setup 단계) ──────────────────────────────────────────
// DataInitializer가 앱 기동 시 각 상품을 100개씩 초기화한다.
// 부하 테스트 중 재고 고갈로 성능 측정이 왜곡되지 않도록
// release API(quantity += N)로 충분한 재고를 사전 확보한다.
export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  for (const productId of PRODUCT_IDS) {
    const res = http.post(
      `${INV_URL}/api/v1/inventory/release`,
      JSON.stringify({ productId, quantity: 9999 }),
      { headers },
    );
    check(res, { [`${productId} 재고 확보`]: (r) => r.status === 200 });
  }
}

// ── 메인 시나리오 ─────────────────────────────────────────────────────────────
export default function () {
  const customerId = CUSTOMER_IDS[Math.floor(Math.random() * CUSTOMER_IDS.length)];
  const productId  = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const quantity   = Math.floor(Math.random() * 3) + 1;

  const headers = { 'Content-Type': 'application/json' };
  const payload = JSON.stringify({
    customerId,
    items: [{ productId, quantity }],
  });

  const start = Date.now();
  const res   = http.post(`${BASE_URL}/api/v1/orders`, payload, {
    headers,
    timeout: '5s',
  });
  orderDuration.add(Date.now() - start);

  check(res, {
    'status is 201':          (r) => r.status === 201,
    'response is array':      (r) => Array.isArray(r.json()),
    'first order has id':     (r) => r.json()[0]?.id !== undefined,
    'first order has status': (r) => r.json()[0]?.status !== undefined,
  });

  // ── Saga 정상 종료율 측정 ──────────────────────────────────────────────────
  // 주문 생성 직후 상태는 PENDING이므로, 최종 상태(CONFIRMED/CANCELLED)가 될
  // 때까지 최대 5초(10회 × 500ms) 폴링한다.
  const orderId = res.status === 201 ? res.json()[0]?.id : null;
  if (orderId) {
    let settled = false;
    for (let i = 0; i < 10; i++) {
      sleep(0.5);
      const poll = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, { headers });
      if (poll.status === 200) {
        const status = poll.json()?.status;
        if (status === 'CONFIRMED' || status === 'CANCELLED') {
          sagaSuccessRate.add(true);
          settled = true;
          break;
        }
      }
    }
    if (!settled) {
      sagaSuccessRate.add(false);
    }
  } else {
    sagaSuccessRate.add(false);
  }

  sleep(Math.random() * 0.5 + 0.5);
}

// ── 결과 요약 (teardown 단계) ─────────────────────────────────────────────────
export function teardown() {
  console.log('=== 테스트 완료 ===');
  console.log(`BASE_URL: ${BASE_URL}`);
}
