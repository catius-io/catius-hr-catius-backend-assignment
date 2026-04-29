package com.catius.order.client.dto;

public record ReserveInventoryRequest(Long productId, int quantity) {
}
