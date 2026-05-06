package com.catius.inventory.controller.dto.response;

import com.catius.inventory.domain.Inventory;

public record InventoryResponse(
        Long productId,
        String productName,
        int quantity
) {
    public static InventoryResponse from(Inventory stock) {
        return new InventoryResponse(
                stock.getProductId(),
                stock.getProductName(),
                stock.getQuantity()
        );
    }
}
