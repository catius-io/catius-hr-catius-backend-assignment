package com.catius.order.client.dto.request;


public record InventoryRequest(
        Long productId,
        int quantity
) {
}
