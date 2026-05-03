package com.catius.order.service.event;

import com.catius.order.domain.AttemptedItem;
import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationReason;

import java.util.List;

/**
 * inventory.release-requested.v1 토픽 페이로드 (ADR-005). 멱등 키는 (orderId, productId)이며 inventory 측에서
 * tombstone + UNIQUE 제약으로 처리 (ADR-002 / ADR-003).
 *
 * <p>{@code reason}은 운영 진단용 — inventory 측 처리 분기에 영향을 주지 않는다.
 */
public record InventoryReleaseRequestedEvent(
        String orderId,
        List<Item> items,
        String reason
) {

    public record Item(long productId, int quantity) {
        static Item from(AttemptedItem a) {
            return new Item(a.productId(), a.quantity());
        }
    }

    public static InventoryReleaseRequestedEvent from(PendingCompensation comp) {
        PendingCompensationReason reason = comp.getReason();
        return new InventoryReleaseRequestedEvent(
                comp.getOrderId(),
                comp.getAttemptedItems().stream().map(Item::from).toList(),
                reason == null ? null : reason.name()
        );
    }
}
