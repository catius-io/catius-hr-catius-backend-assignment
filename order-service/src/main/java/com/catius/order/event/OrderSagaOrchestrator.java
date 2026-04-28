package com.catius.order.event;

import com.catius.order.client.InventoryClientFacade;
import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.domain.Order;
import com.catius.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private static final int STATUS_SAVE_MAX_ATTEMPTS = 3;
    private static final long STATUS_SAVE_RETRY_DELAY_MS = 100;

    private final InventoryClientFacade inventoryClientFacade;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderRepository orderRepository;

    public void execute(Order order) {
        boolean reserved = false;

        try {
            log.info("[재고 차감] orderId={} productId={}, qty={}",
                    order.getId(), order.getProductId(), order.getQuantity());
            inventoryClientFacade.reserve(new InventoryRequest(order.getProductId(), order.getQuantity()));
            reserved = true;

            log.info("[주문 확인] orderId={}", order.getId());
            order.confirm();
            saveOrderStatusWithRetry(order, "confirm");

            log.info("[주문 이벤트 발행] orderId={}", order.getId());
            orderEventPublisher.publishOrderConfirmed(new OrderConfirmedEvent(
                    order.getId(),
                    order.getProductId(),
                    order.getQuantity(),
                    order.getStatus().name(),
                    System.currentTimeMillis()
            ));
            log.info("[주문 이벤트 성공] orderId={}", order.getId());

        } catch (Exception e) {
            log.warn("[주문 실패] orderId={} cause={}", order.getId(), e.getMessage());
            order.cancel();
            try {
                saveOrderStatusWithRetry(order, "cancel");
            } catch (CannotAcquireLockException lockException) {
                log.error("[주문 취소 상태 저장 실패] orderId={} — 수동 확인 필요, cause={}",
                        order.getId(), lockException.getMessage());
            }

            if (reserved) {
                try {
                    inventoryClientFacade.release(new InventoryRequest(order.getProductId(), order.getQuantity()));
                    log.info("[재고 복구 완료] orderId={}", order.getId());
                } catch (Exception releaseEx) {
                    log.error("[재고 복구 실패] orderId={} — 수동 복구 필요, cause={}",
                            order.getId(), releaseEx.getMessage());
                }
            }
        }
    }

    private void saveOrderStatusWithRetry(Order order, String action) {
        CannotAcquireLockException lastException = null;

        for (int attempt = 1; attempt <= STATUS_SAVE_MAX_ATTEMPTS; attempt++) {
            try {
                orderRepository.save(order);
                return;
            } catch (CannotAcquireLockException e) {
                lastException = e;
                log.warn("[주문 상태 저장 재시도] action={} orderId={} attempt={}/{} cause={}",
                        action, order.getId(), attempt, STATUS_SAVE_MAX_ATTEMPTS, e.getMessage());
                if (attempt < STATUS_SAVE_MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }

        throw lastException;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(STATUS_SAVE_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying order status save", interruptedException);
        }
    }
}
