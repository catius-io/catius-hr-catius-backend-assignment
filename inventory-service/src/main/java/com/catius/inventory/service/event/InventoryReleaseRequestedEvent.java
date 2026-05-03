package com.catius.inventory.service.event;

import java.util.List;

/**
 * inventory.release-requested.v1 토픽 페이로드 (ADR-005). order-service에서 발행되어 본 서비스에서 소비.
 *
 * <p>order-service에 동일 이름의 record가 별도로 존재 — 마이크로서비스 분리 원칙으로 의도적 중복.
 */
public record InventoryReleaseRequestedEvent(
        String orderId,
        List<Item> items,
        String reason
) {

    public record Item(long productId, int quantity) {
    }
}
