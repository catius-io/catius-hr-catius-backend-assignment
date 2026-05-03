package com.catius.inventory.service.kafka;

import com.catius.inventory.service.InventoryReservationService;
import com.catius.inventory.service.ReleaseOutcome;
import com.catius.inventory.service.event.InventoryReleaseRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order-service의 보상 이벤트를 소비하여 item별 release를 호출 (ADR-003).
 *
 * <p>release는 멱등이라 at-least-once 전달 + 부분 실패 후 재처리에 안전:
 * <ul>
 *   <li>이미 RELEASED 상태 → no-op</li>
 *   <li>reservation 부재(release-before-reserve race) → tombstone INSERT (ADR-002)</li>
 * </ul>
 *
 * <p>리스너에서 예외 발생 시 컨테이너가 retry. 영구 실패는 운영 환경에서 DLQ로 격리하지만 본 과제 범위 외.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReleaseRequestedListener {

    private final InventoryReservationService service;

    @KafkaListener(topics = "${catius.kafka.topics.inventory-release-requested}")
    public void onReleaseRequested(InventoryReleaseRequestedEvent event) {
        log.info("release requested: orderId={}, items={}, reason={}",
                event.orderId(), event.items().size(), event.reason());
        for (InventoryReleaseRequestedEvent.Item item : event.items()) {
            ReleaseOutcome outcome = service.release(event.orderId(), item.productId());
            log.debug("release outcome: orderId={}, productId={}, outcome={}",
                    event.orderId(), item.productId(), outcome);
        }
    }
}
