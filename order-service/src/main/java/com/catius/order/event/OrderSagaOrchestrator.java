package com.catius.order.event;

import com.catius.order.client.InventoryClient;
import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.domain.Order;
import com.catius.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final InventoryClient inventoryClient;
    private final OrderEventPublisher orderEventPublisher;

    public void execute(Order order) {

        try {
            log.info("[재고 차감] orderId={} productId={}, qty={}",
                    order.getId(), order.getProductId(), order.getQuantity());
            inventoryClient.reserve(new InventoryRequest(order.getProductId(), order.getQuantity()));

            log.info("[주문 확인] orderId={} ", order.getId());
            order.confirm();

            log.info("[주문 이벤트 발행] orderId={} ", order.getId());
            orderEventPublisher.publishOrderConfirmed(new OrderConfirmedEvent(
                    order.getId(),
                    order.getProductId(),
                    order.getQuantity(),
                    order.getStatus().name(),
                    System.currentTimeMillis()
            ));

            log.info("[주문 이벤트 성공] orderId={} ", order.getId());

        } catch (Exception e) {
            order.cancel();
            inventoryClient.release(new InventoryRequest(order.getProductId(), order.getQuantity()));
            log.info("[주문 실패] orderId={} | cancelled", order.getId());
        }

    }


}
