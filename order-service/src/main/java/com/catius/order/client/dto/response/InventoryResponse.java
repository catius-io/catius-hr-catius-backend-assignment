package com.catius.order.client.dto.response;

import com.catius.inventory.domain.Inventory;

public record InventoryResponse(
        String productId,
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
