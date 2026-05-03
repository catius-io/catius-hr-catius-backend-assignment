package com.catius.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Saga 보상 후보 영속화 (ADR-007 boundary outbox — 보상 토픽에만 적용).
 * order_id 단위 유일 row. Order 영속화 트랜잭션에서 COMPLETED로 마킹되어 부팅 복구 스캔에서 빠진다 (race 방지).
 */
@Entity
@Table(name = "pending_compensations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingCompensation {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "attempted_items_json", columnDefinition = "TEXT")
    @Convert(converter = AttemptedItemsConverter.class)
    private List<AttemptedItem> attemptedItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingCompensationStatus status;

    @Enumerated(EnumType.STRING)
    private PendingCompensationReason reason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastAttemptAt;

    private int attemptCount;

    private PendingCompensation(String orderId) {
        this.orderId = orderId;
        this.attemptedItems = new ArrayList<>();
        this.status = PendingCompensationStatus.IN_PROGRESS;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PendingCompensation start(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        return new PendingCompensation(orderId);
    }

    public List<AttemptedItem> getAttemptedItems() {
        return Collections.unmodifiableList(attemptedItems);
    }

    public void appendAttempted(AttemptedItem item) {
        Objects.requireNonNull(item, "item");
        this.attemptedItems.add(item);
        touch();
    }

    public void removeAttempted(long productId) {
        this.attemptedItems.removeIf(i -> i.productId() == productId);
        touch();
    }

    /**
     * 보상 발행 대상으로 전이. attempted_items에 남은 것이 없으면 호출자가 {@link #markCompleted()}로 분기해야 한다.
     */
    public void markReadyToPublish(PendingCompensationReason reason) {
        Objects.requireNonNull(reason, "reason");
        this.status = PendingCompensationStatus.READY_TO_PUBLISH;
        this.reason = reason;
        touch();
    }

    /**
     * 보상 불필요 — 정상 확정(Order INSERT와 같은 트랜잭션) 또는 explicit failure 후 attempted가 비어있는 경우.
     */
    public void markCompleted() {
        this.status = PendingCompensationStatus.COMPLETED;
        touch();
    }

    public void markPublished() {
        this.status = PendingCompensationStatus.PUBLISHED;
        this.lastAttemptAt = Instant.now();
        this.attemptCount++;
        touch();
    }

    public void markDispatchFailed() {
        this.status = PendingCompensationStatus.DISPATCH_FAILED;
        this.lastAttemptAt = Instant.now();
        this.attemptCount++;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
