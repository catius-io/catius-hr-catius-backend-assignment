package com.catius.order.infrastructure.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(name = "inventory-service", url = "\${inventory-service.url:http://localhost:8082}")
interface InventoryFeignClient {

    @PostMapping("/api/v1/inventory/reserve")
    fun reserve(@RequestBody request: InventoryRequest)

    @PostMapping("/api/v1/inventory/release")
    fun release(@RequestBody request: InventoryRequest)

    data class InventoryRequest(
        val orderId: Long,
        val items: List<Item>,
    ) {
        data class Item(
            val productId: Long,
            val quantity: Int,
        )
    }
}
