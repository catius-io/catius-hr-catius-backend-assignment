package com.catius.order.service;

import com.catius.order.domain.AttemptedItem;
import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationReason;
import com.catius.order.domain.PendingCompensationStatus;
import com.catius.order.repository.PendingCompensationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * pending_compensations 라이프사이클의 트랜잭션 단위 변이 (ADR-007).
 *
 * <p>Saga orchestrator와 분리한 이유: 각 변이가 짧은 독립 트랜잭션이어야 하고
 * (HTTP reserve 호출 사이에 commit 되어야 보상 후보가 영속화됨), 같은 클래스 안에서 self-invocation은
 * Spring AOP의 {@code @Transactional} 프록시를 우회하기 때문.
 *
 * <p><b>예외</b>: Order INSERT와 함께 COMPLETED 마킹은 atomic이어야 하므로 본 클래스가 아니라
 * {@code OrderSagaService}의 {@code TransactionTemplate}에서 직접 수행한다.
 */
@Service
@RequiredArgsConstructor
public class CompensationTracker {

    private final PendingCompensationRepository repository;

    @Transactional
    public void start(String orderId) {
        repository.save(PendingCompensation.start(orderId));
    }

    @Transactional
    public void appendAttempted(String orderId, AttemptedItem item) {
        PendingCompensation comp = load(orderId);
        comp.appendAttempted(item);
        repository.save(comp);
    }

    @Transactional
    public PendingCompensationStatus removeAttemptedAndDecide(
            String orderId, long failedProductId, PendingCompensationReason reason) {
        PendingCompensation comp = load(orderId);
        comp.removeAttempted(failedProductId);
        if (comp.getAttemptedItems().isEmpty()) {
            comp.markCompleted();
        } else {
            comp.markReadyToPublish(reason);
        }
        repository.save(comp);
        return comp.getStatus();
    }

    @Transactional
    public void markReadyToPublish(String orderId, PendingCompensationReason reason) {
        PendingCompensation comp = load(orderId);
        comp.markReadyToPublish(reason);
        repository.save(comp);
    }

    @Transactional
    public void markPublished(String orderId) {
        PendingCompensation comp = load(orderId);
        comp.markPublished();
        repository.save(comp);
    }

    @Transactional
    public void markDispatchFailed(String orderId) {
        PendingCompensation comp = load(orderId);
        comp.markDispatchFailed();
        repository.save(comp);
    }

    @Transactional
    public void markCompleted(String orderId) {
        PendingCompensation comp = load(orderId);
        comp.markCompleted();
        repository.save(comp);
    }

    private PendingCompensation load(String orderId) {
        return repository.findById(orderId).orElseThrow(() -> new IllegalStateException(
                "pending_compensations row not found: orderId=" + orderId));
    }
}
