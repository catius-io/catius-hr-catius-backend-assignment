package com.catius.inventory.controller.dto.request;


public record InventoryRequest(
        Long productId,
        int quantity
) {
}
