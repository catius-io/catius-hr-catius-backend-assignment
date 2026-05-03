package com.catius.order.service;

import com.catius.order.domain.AttemptedItem;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationReason;
import com.catius.order.domain.PendingCompensationStatus;
import com.catius.order.repository.OrderRepository;
import com.catius.order.repository.PendingCompensationRepository;
import com.catius.order.service.event.InventoryReleaseRequestedEvent;
import com.catius.order.service.event.OrderConfirmedEvent;
import com.catius.order.service.exception.AlreadyCompensatedException;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.InsufficientStockException;
import com.catius.order.service.exception.ProductNotFoundException;
import com.catius.order.service.exception.ReservationConflictException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * order-service Saga orchestrator (ADR-003).
 *
 * <p>흐름: items[] → 각 item별 sequential inventory.reserve → 전부 성공 시 Order INSERT + COMPLETED
 * 마킹(같은 트랜잭션) → confirmed 발행. 실패 분기는 explicit / ambiguous / persist failure 세 갈래.
 *
 * <p>{@link TransactionTemplate}을 쓰는 이유: Order INSERT + pending_compensations.COMPLETED 마킹이
 * 같은 트랜잭션이어야 하는데 ({@link CompensationTracker} 호출은 별도 프록시이므로 합쳐지지 않음),
 * 자기 클래스 내 {@code @Transactional} 메서드는 self-invocation으로 AOP 우회되어 동작 안 함.
 */
@Service
@Slf4j
public class OrderSagaService {

    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final PendingCompensationRepository pendingRepository;
    private final CompensationTracker tracker;
    private final OrderEventPublisher publisher;
    private final TransactionTemplate txTemplate;
    private final Counter confirmedDispatchFailedCounter;
    private final Counter compensationDispatchFailedCounter;

    public OrderSagaService(InventoryClient inventoryClient,
                            OrderRepository orderRepository,
                            PendingCompensationRepository pendingRepository,
                            CompensationTracker tracker,
                            OrderEventPublisher publisher,
                            PlatformTransactionManager txManager,
                            MeterRegistry meterRegistry) {
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.pendingRepository = pendingRepository;
        this.tracker = tracker;
        this.publisher = publisher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.confirmedDispatchFailedCounter = meterRegistry.counter("order.saga.confirmed_dispatch_failed");
        this.compensationDispatchFailedCounter = meterRegistry.counter("order.saga.compensation_dispatch_failed");
    }

    public Order createOrder(long customerId, List<OrderItem> items) {
        // service 경계 입력 검증 — controller 우회 호출 시에도 빈 items / 중복 productId / 잘못된 customerId
        // 가 saga 진입을 막아 pending_compensations row 생성과 빈 보상 이벤트 발행을 방지 (ADR-001).
        Order.validateOrderInput(customerId, items);

        String orderId = UUID.randomUUID().toString();
        tracker.start(orderId);

        for (OrderItem item : items) {
            tracker.appendAttempted(orderId, AttemptedItem.from(item));
            try {
                inventoryClient.reserve(orderId, item.getProductId(), item.getQuantity());
            } catch (InsufficientStockException | AlreadyCompensatedException
                    | ReservationConflictException | ProductNotFoundException e) {
                handleExplicit(orderId, item.getProductId());
                throw e;
            } catch (AmbiguousInventoryException e) {
                handleAmbiguous(orderId, PendingCompensationReason.AMBIGUOUS_FAILURE);
                throw e;
            } catch (CallNotPermittedException e) {
                // CB OPEN — 실제 호출이 inventory에 도달하지 않았을 가능성이 높지만
                // 직전 누적 실패가 ambiguous였을 수 있어 보수적으로 ambiguous 처리.
                handleAmbiguous(orderId, PendingCompensationReason.AMBIGUOUS_FAILURE);
                throw new AmbiguousInventoryException(
                        "inventory circuit breaker open: " + e.getMessage(), e);
            }
        }

        Order order;
        try {
            order = txTemplate.execute(status -> {
                Order o = Order.confirmed(orderId, customerId, items);
                orderRepository.save(o);
                PendingCompensation comp = pendingRepository.findById(orderId)
                        .orElseThrow(() -> new IllegalStateException(
                                "pending_compensations missing during persist: orderId=" + orderId));
                comp.markCompleted();
                pendingRepository.save(comp);
                return o;
            });
        } catch (RuntimeException e) {
            // Order persist 트랜잭션 rollback → pending_compensations 도 IN_PROGRESS로 유지됨.
            // 모든 attempted_items에 대해 보상 발행 (ADR-003 persist failure 분기).
            handleAmbiguous(orderId, PendingCompensationReason.PERSIST_FAILURE);
            throw e;
        }

        // confirmed 발행 실패는 사용자 응답 201을 깨뜨리지 않음 (ADR-007 정책).
        // pending_compensations.status는 이미 COMPLETED로 commit되어 있어 부팅 복구 미트리거 — 핵심 race 방지.
        try {
            publisher.publishConfirmed(OrderConfirmedEvent.from(order));
        } catch (RuntimeException e) {
            log.error("confirmed publish failed (order persisted, response will still be 201): orderId={}",
                    orderId, e);
            confirmedDispatchFailedCounter.increment();
        }

        return order;
    }

    private void handleExplicit(String orderId, long failedProductId) {
        PendingCompensationStatus status = tracker.removeAttemptedAndDecide(
                orderId, failedProductId, PendingCompensationReason.EXPLICIT_FAILURE);
        if (status == PendingCompensationStatus.READY_TO_PUBLISH) {
            dispatchCompensation(orderId);
        }
    }

    private void handleAmbiguous(String orderId, PendingCompensationReason reason) {
        tracker.markReadyToPublish(orderId, reason);
        dispatchCompensation(orderId);
    }

    private void dispatchCompensation(String orderId) {
        PendingCompensation comp = pendingRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "pending_compensations missing during dispatch: orderId=" + orderId));
        try {
            publisher.publishCompensation(InventoryReleaseRequestedEvent.from(comp));
            tracker.markPublished(orderId);
        } catch (RuntimeException e) {
            log.error("compensation publish failed: orderId={}", orderId, e);
            tracker.markDispatchFailed(orderId);
            compensationDispatchFailedCounter.increment();
            // 사용자 응답은 진행 중인 4xx/5xx로 그대로 — 부팅 복구가 재발행 책임.
        }
    }
}
