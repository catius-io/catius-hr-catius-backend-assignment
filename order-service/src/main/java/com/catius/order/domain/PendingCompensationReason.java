package com.catius.order.domain;

/**
 * 보상이 발행되어야 할 사유 (ADR-007 라이프사이클 표).
 */
public enum PendingCompensationReason {
    /** 명시적 4xx — 차감 없음 확정. 실패 item은 attempted에서 제거됨 */
    EXPLICIT_FAILURE,
    /** 5xx / timeout / decode 실패 — 차감 여부 불명확. at-least-once 보상 */
    AMBIGUOUS_FAILURE,
    /** reserve 전부 성공 후 Order persist 단계에서 실패 — attempted 전체 보상 */
    PERSIST_FAILURE,
    /** 부팅 시점에 IN_PROGRESS row 발견 — 프로세스가 reserve 흐름 중간에 죽었음 */
    CRASH_RECOVERY
}
