package com.catius.order.service;

import com.catius.order.client.InventoryGateway;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.domain.Order;
import com.catius.order.messaging.OrderConfirmedEvent;
import com.catius.order.messaging.OrderEventPublishException;
import com.catius.order.messaging.OrderEventPublisher;
import com.catius.order.repository.OrderRepository;
import com.catius.order.service.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 주문 생성 Saga (orchestration 스타일).
 *
 * 흐름:
 *   1. inventoryGateway.reserve(...)         ← reserve 실패 (영구) → 즉시 throw, Order 생성 안 함
 *   2. orderRepository.save(PENDING)          ← DB 실패 시 보상 (release only — order 객체 없음)
 *   3. order.confirm() + save                ← DB 실패 시 보상 (release + COMPENSATED 시도)
 *   4. publisher.publishOrderConfirmed(...)  ← publish 실패 시 보상 (release + COMPENSATED)
 *
 * 트랜잭션 경계 (의도적 결정 — @Transactional 미적용):
 * - createOrder 전체를 @Transactional 로 묶으면 외부 호출(Feign reserve, Kafka publish) 이 TX 안에
 *   포함되어 *분산 트랜잭션* 의 함정에 빠짐 — TX 동안 DB 락 보유 시간 ↑ + Saga 의 compensating
 *   transaction 모델과 부조화. 각 DB 호출(save) 은 Spring Data JPA 의 자체 sub-TX 에 의존.
 *
 * 보상 catch 범위 (의도적 좁힘):
 * - InventoryClientException / OrderEventPublishException / DataAccessException 만 보상 트리거.
 *   세 종류 모두 *인프라 실패* 라 stock 복구가 의미 있음.
 * - 우리 코드의 NPE / IllegalStateException 등은 *통과* — 보상 안 함, 그대로 throw 되어 진단 노출.
 *   (Gateway 의 catch 좁히기와 동일한 원칙: "처리 가능한 것만 잡고 나머지는 통과")
 *
 * order == null 시나리오:
 * - PENDING save 자체가 DataAccessException 으로 실패하면 order 변수가 할당 전. compensate 가
 *   *release 만* best-effort 로 시도하고 COMPENSATED 마킹은 skip (저장된 order 가 없음).
 *
 * 인터페이스 분리 안 함: InventoryService 와 동일 패턴 — 단일 구현이라 추상화 가치 없음 (YAGNI).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final InventoryGateway inventoryGateway;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher publisher;

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order createOrder(Long productId, int quantity) {
        // Step 1. reserve — 영구 실패는 여기서 throw, Order 생성 안 함.
        inventoryGateway.reserve(productId, quantity);

        // 이 시점부터 stock 은 차감 상태 — 어떤 실패도 release 보상 필요.
        Order order = null;
        try {
            order = orderRepository.save(Order.create(productId, quantity));
            order.confirm();
            orderRepository.save(order);
            publisher.publishOrderConfirmed(new OrderConfirmedEvent(
                    UUID.randomUUID().toString(),
                    order.getId(),
                    order.getProductId(),
                    order.getQuantity(),
                    Instant.now()
            ));
            return order;
        } catch (InventoryClientException | OrderEventPublishException | DataAccessException e) {
            compensate(order, productId, quantity, e);
            throw e;
        }
    }

    /**
     * reserve 성공 후 후속 단계 실패 시 release 로 stock 복원 + Order COMPENSATED 마킹.
     *
     * 보상 우선순위:
     * 1. release 호출 (stock 복구 — 가장 중요)
     * 2. order COMPENSATED 마킹 (Order 객체가 영속화됐을 때만)
     *
     * 어느 쪽도 실패하면 *원인* 예외를 우선 throw, 보상 실패는 로그로 운영 정정 영역 위임.
     */
    private void compensate(Order order, Long productId, int quantity, Throwable cause) {
        String ref = (order != null && order.getId() != null)
                ? "orderId=" + order.getId()
                : "no-order-persisted";
        log.warn("compensating {} after failure: {}", ref, cause.toString());

        try {
            inventoryGateway.release(productId, quantity);
        } catch (InventoryClientException releaseFailure) {
            log.error("compensation release failed for {} — manual reconciliation needed",
                    ref, releaseFailure);
            return;
        }

        // PENDING save 자체 실패 케이스: 영속화된 order 가 없음. release 만으로 보상 종료.
        if (order == null || order.getId() == null) {
            return;
        }

        try {
            order.compensate();
            orderRepository.save(order);
        } catch (DataAccessException dbFailure) {
            log.error("compensation save failed for {} — release succeeded but COMPENSATED state not persisted",
                    ref, dbFailure);
        }
    }
}
