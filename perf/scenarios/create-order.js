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
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const orderDuration   = new Trend('order_create_duration', true);

const BASE_URL  = __ENV.BASE_URL || 'http://localhost:8081';
const INV_URL   = __ENV.INV_URL  || 'http://localhost:8082';

const PRODUCTS = ['PRODUCT-001', 'PRODUCT-002', 'PRODUCT-003', 'PRODUCT-004', 'PRODUCT-005'];

// ── SLO thresholds ────────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 5 },  // ramp-up
    { duration: '1m',  target: 10 },  // steady-state
    { duration: '20s', target: 0  },  // ramp-down
  ],
  thresholds: {
    // 응답시간 SLO
    http_req_duration:    ['p(95)<500', 'p(99)<1000'],
    // 에러율 SLO  (4xx/5xx 포함)
    http_req_failed:      ['rate<0.01'],
    // 주문 생성 전용 응답시간 SLO
    order_create_duration:['p(95)<500'],
  },
};

// ── 테스트 데이터 준비 (setup 단계) ──────────────────────────────────────────
// DataInitializer가 앱 기동 시 각 상품을 100개씩 초기화한다.
// 부하 테스트 중 재고 고갈로 주문 생성 성능 측정이 왜곡되지 않도록
// release API(quantity += N)로 충분한 재고를 사전 확보한다.
export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  for (const productId of PRODUCTS) {
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
  const productId = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
  const quantity  = Math.floor(Math.random() * 3) + 1;

  const headers = { 'Content-Type': 'application/json' };
  const payload = JSON.stringify({ productId, quantity });

  const start = Date.now();
  const res   = http.post(`${BASE_URL}/api/v1/orders`, payload, {
    headers,
    timeout: '5s',
  });
  orderDuration.add(Date.now() - start);

  const hasResponseBody = res.body !== null && res.body !== undefined && res.body !== '';

  check(res, {
    'status is 201':          (r) => r.status === 201,
    'response has id':        (r) => hasResponseBody && r.json('id') !== null,
    'response has status':    (r) => hasResponseBody && r.json('status') !== undefined,
  });

  sleep(Math.random() * 0.5 + 0.5);
}

// ── 결과 요약 (teardown 단계) ─────────────────────────────────────────────────
export function teardown() {
  console.log('=== 테스트 완료 ===');
  console.log(`BASE_URL: ${BASE_URL}`);
}
