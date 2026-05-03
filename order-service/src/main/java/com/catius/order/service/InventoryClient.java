package com.catius.order.service;

/**
 * inventory-service에 대한 outbound 포트 (ADR-001 outbound DI).
 * 구현체는 service/feign/ 서브패키지에 위치.
 *
 * <p>실패 모델은 ADR-003에 따라 두 종류로 분기된다:
 * <ul>
 *   <li><b>명시적 4xx</b>: 호출이 거부되어 차감이 없음이 확정 — 도메인 예외로 throw</li>
 *   <li><b>ambiguous (5xx/timeout/connection)</b>: 차감 여부 불명확 — {@code AmbiguousInventoryException}</li>
 * </ul>
 */
public interface InventoryClient {

    InventoryView getInventory(long productId);

    void reserve(String orderId, long productId, int quantity);

    void release(String orderId, long productId);
}
