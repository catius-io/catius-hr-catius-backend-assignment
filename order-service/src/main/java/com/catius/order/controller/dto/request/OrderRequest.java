package com.catius.order.controller.dto.request;

import java.util.List;

public record OrderRequest(
        Long customerId,
        List<OrderItemRequest> items
) {
}
