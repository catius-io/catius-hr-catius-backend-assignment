package com.catius.order.service;

import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationReason;
import com.catius.order.domain.PendingCompensationStatus;
import com.catius.order.repository.PendingCompensationRepository;
import com.catius.order.service.event.InventoryReleaseRequestedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅 시점에 미완료 보상 row를 스캔하여 재발행 (ADR-007).
 *
 * <p>스캔 대상 상태:
 * <ul>
 *   <li>{@link PendingCompensationStatus#IN_PROGRESS}: reserve 흐름 중간에 프로세스가 다운됨 — {@link PendingCompensationReason#CRASH_RECOVERY}로 표시 후 발행 (release 멱등성이 false-positive를 무해화)</li>
 *   <li>{@link PendingCompensationStatus#READY_TO_PUBLISH}: 발행 직전에 다운됨 — 그대로 재발행</li>
 *   <li>{@link PendingCompensationStatus#DISPATCH_FAILED}: 직전 발행이 실패 — 재시도</li>
 * </ul>
 *
 * <p>{@link PendingCompensationStatus#COMPLETED} / {@link PendingCompensationStatus#PUBLISHED} row는 스캔 대상 아님 —
 * 이미 확정된 주문의 재고를 부당 release하지 않도록 보장하는 핵심 race 방지선.
 *
 * <p>Order={@code @ApplicationRunner}는 컨텍스트가 완전히 준비된 후 실행되므로 KafkaTemplate / DB 모두 사용 가능.
 */
@Component
@Slf4j
public class CompensationRecoveryRunner implements ApplicationRunner {

    private final PendingCompensationRepository repository;
    private final OrderEventPublisher publisher;
    private final CompensationTracker tracker;
    private final Counter compensationDispatchFailedCounter;

    public CompensationRecoveryRunner(PendingCompensationRepository repository,
                                      OrderEventPublisher publisher,
                                      CompensationTracker tracker,
                                      MeterRegistry meterRegistry) {
        this.repository = repository;
        this.publisher = publisher;
        this.tracker = tracker;
        this.compensationDispatchFailedCounter = meterRegistry.counter("order.saga.compensation_dispatch_failed");
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    /**
     * 주기적 sweeper — 부팅 시점 외에도 발행 실패 row를 회수한다.
     * Kafka가 앱 재시작 없이 복구되는 운영 시나리오에서 DISPATCH_FAILED row가 다음 재기동까지 남는 한계를 닫는다.
     * 인터벌은 application.yml의 {@code order.compensation.sweeper-interval-ms}로 조정 (기본 30초).
     */
    @Scheduled(
            fixedDelayString = "${order.compensation.sweeper-interval-ms:30000}",
            initialDelayString = "${order.compensation.sweeper-interval-ms:30000}"
    )
    public void scheduledSweep() {
        recover();
    }

    /**
     * 테스트에서 직접 호출하기 위해 분리.
     */
    public void recover() {
        List<PendingCompensation> targets = repository.findByStatusIn(List.of(
                PendingCompensationStatus.IN_PROGRESS,
                PendingCompensationStatus.READY_TO_PUBLISH,
                PendingCompensationStatus.DISPATCH_FAILED
        ));
        if (targets.isEmpty()) {
            log.info("compensation recovery: no pending rows");
            return;
        }
        log.info("compensation recovery: {} pending rows found", targets.size());
        for (PendingCompensation comp : targets) {
            recoverOne(comp.getOrderId(), comp.getStatus());
        }
    }

    private void recoverOne(String orderId, PendingCompensationStatus initialStatus) {
        // IN_PROGRESS 인 경우 reason 마킹부터 (다른 status는 reason이 이미 설정돼 있음).
        if (initialStatus == PendingCompensationStatus.IN_PROGRESS) {
            tracker.markReadyToPublish(orderId, PendingCompensationReason.CRASH_RECOVERY);
        }

        PendingCompensation comp = repository.findById(orderId).orElse(null);
        if (comp == null) {
            log.warn("compensation recovery: row vanished mid-recovery: orderId={}", orderId);
            return;
        }

        if (comp.getAttemptedItems().isEmpty()) {
            // 보상할 자원 없음 — 그대로 종결.
            tracker.markCompleted(orderId);
            return;
        }

        try {
            publisher.publishCompensation(InventoryReleaseRequestedEvent.from(comp));
            tracker.markPublished(orderId);
            log.info("compensation recovery published: orderId={}, items={}",
                    orderId, comp.getAttemptedItems().size());
        } catch (RuntimeException e) {
            log.error("compensation recovery publish failed: orderId={}", orderId, e);
            tracker.markDispatchFailed(orderId);
            compensationDispatchFailedCounter.increment();
        }
    }
}
