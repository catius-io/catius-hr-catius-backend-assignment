package com.catius.order.domain;

/**
 * Saga 흐름의 4-state 모델.
 *
 *   PENDING  ──confirm()──→  CONFIRMED       (정상 흐름 완료)
 *            ──markFailed()──→  FAILED       (reserve 자체 실패 — 보상 불필요)
 *            ──markCompensated()──→  COMPENSATED  (reserve 성공 후 confirm/publish 실패 → release 로 보상 완료)
 *
 * 모든 종착 상태(CONFIRMED / FAILED / COMPENSATED) 는 이후 전이 불가 (불변).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    COMPENSATED
}
