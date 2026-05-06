package com.catius.order.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent {

    private Long orderId;
    private Long customerId;
    private List<OrderItemEvent> items;
    private String status;
    private Long timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private Long productId;
        private int quantity;
    }
}
