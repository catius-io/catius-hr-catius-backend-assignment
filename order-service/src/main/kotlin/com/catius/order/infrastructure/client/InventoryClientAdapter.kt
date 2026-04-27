package com.catius.order.infrastructure.client

import com.catius.order.domain.OrderItem
import com.catius.order.domain.port.InventoryClient
import com.catius.order.domain.port.ReserveOutcome
import feign.FeignException
import org.springframework.stereotype.Component

@Component
class InventoryClientAdapter(
    private val inventoryFeignClient: InventoryFeignClient,
) : InventoryClient {

    override fun reserve(orderId: Long, items: List<OrderItem>): ReserveOutcome {
        return try {
            inventoryFeignClient.reserve(
                InventoryFeignClient.InventoryRequest(
                    orderId = orderId,
                    items = items.map { InventoryFeignClient.InventoryRequest.Item(it.productId, it.quantity) },
                ),
            )
            ReserveOutcome.Success
        } catch (e: FeignException.Conflict) {
            // 409 Conflict: 재고 부족
            // 실제 응답 바디에서 productId를 추출할 수도 있지만, 일단은 첫 번째 아이템 혹은 전체 실패로 간주
            ReserveOutcome.InsufficientStock(items.first().productId)
        } catch (e: Exception) {
            // 그 외 (5xx, 타임아웃, CB 오픈 등)
            ReserveOutcome.Unavailable
        }
    }

    override fun release(orderId: Long, items: List<OrderItem>) {
        try {
            inventoryFeignClient.release(
                InventoryFeignClient.InventoryRequest(
                    orderId = orderId,
                    items = items.map { InventoryFeignClient.InventoryRequest.Item(it.productId, it.quantity) },
                ),
            )
        } catch (e: Exception) {
            // 보상 트랜잭션 실패 시 로깅 및 후속 조치 필요 (여기선 단순 무시 혹은 로깅)
        }
    }
}
