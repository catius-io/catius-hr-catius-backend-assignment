package com.catius.order.client.dto.request;


public record InventoryRequest(
        String productId,
        int quantity
) {
}
