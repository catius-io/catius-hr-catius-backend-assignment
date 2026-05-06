package com.catius.order.event;

import com.catius.order.client.InventoryClientFacade;
import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.exception.KafkaPublishException;
import com.catius.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final InventoryClientFacade inventoryClientFacade;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderRepository orderRepository;

    public void execute(Order order) {
        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            for (OrderItem item : order.getItems()) {
                log.info("[재고 차감] orderId={} productId={}, qty={}",
                        order.getId(), item.getProductId(), item.getQuantity());
                inventoryClientFacade.reserve(new InventoryRequest(item.getProductId(), item.getQuantity()));
                reservedItems.add(item);
            }

            log.info("[주문 확인] orderId={}", order.getId());
            order.confirm();
            orderRepository.save(order);

            log.info("[주문 이벤트 발행] orderId={}", order.getId());
            orderEventPublisher.publishOrderConfirmed(new OrderConfirmedEvent(
                    order.getId(),
                    order.getCustomerId(),
                    order.getItems().stream()
                            .map(i -> new OrderConfirmedEvent.OrderItemEvent(i.getProductId(), i.getQuantity()))
                            .toList(),
                    order.getStatus().name(),
                    System.currentTimeMillis()
            ));
            log.info("[주문 이벤트 성공] orderId={}", order.getId());

        } catch (KafkaPublishException e) {
            log.error("[Kafka 발행 실패] orderId={} — 주문은 CONFIRMED 유지, 수동 재처리 필요, cause={}",
                    order.getId(), e.getMessage());

        } catch (Exception e) {
            log.warn("[주문 실패] orderId={} cause={}", order.getId(), e.getMessage());
            order.cancel();
            orderRepository.save(order);

            for (OrderItem item : reservedItems) {
                try {
                    inventoryClientFacade.release(new InventoryRequest(item.getProductId(), item.getQuantity()));
                    log.info("[재고 복구 완료] orderId={} productId={}", order.getId(), item.getProductId());
                } catch (Exception releaseEx) {
                    log.error("[재고 복구 실패] orderId={} productId={} — 수동 복구 필요, cause={}",
                            order.getId(), item.getProductId(), releaseEx.getMessage());
                }
            }
        }
    }
}
