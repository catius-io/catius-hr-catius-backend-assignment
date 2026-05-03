package com.catius.order.domain;

/**
 * pending_compensations 라이프사이클 상태 (ADR-007).
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS}: Saga 진행 중. attempted_items에 점진적 append</li>
 *   <li>{@link #READY_TO_PUBLISH}: 발행 대상 결정 (explicit/ambiguous/persist failure/crash recovery)</li>
 *   <li>{@link #PUBLISHED}: 보상 이벤트 발행 성공</li>
 *   <li>{@link #COMPLETED}: 보상 불필요 (정상 확정 또는 attempted 비어있음). Order INSERT와 같은 트랜잭션에서 마킹 — 부팅 복구 스캔 대상에서 제외 (ADR-007 핵심 race 방지)</li>
 *   <li>{@link #DISPATCH_FAILED}: 발행 시도 실패 — 재기동 시 재발행</li>
 * </ul>
 */
public enum PendingCompensationStatus {
    IN_PROGRESS,
    READY_TO_PUBLISH,
    PUBLISHED,
    COMPLETED,
    DISPATCH_FAILED
}
