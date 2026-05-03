package com.catius.order.service.feign.dto;

public record InventoryResponse(
        long productId,
        int quantity
) {
}
