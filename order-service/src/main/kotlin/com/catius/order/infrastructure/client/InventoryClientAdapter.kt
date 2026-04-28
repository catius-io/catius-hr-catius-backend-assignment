package com.catius.order.infrastructure.client

import com.catius.order.domain.OrderItem
import com.catius.order.domain.port.InventoryClient
import com.catius.order.domain.port.ReserveOutcome
import feign.FeignException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.timelimiter.annotation.TimeLimiter
import org.springframework.stereotype.Component

@Component
class InventoryClientAdapter(
    private val inventoryFeignClient: InventoryFeignClient,
) : InventoryClient {

    @CircuitBreaker(name = "inventoryClient", fallbackMethod = "reserveFallback")
    @Retry(name = "inventoryClient")
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
            // 409 Conflict는 비즈니스 예외로 취급하여 직접 처리
            ReserveOutcome.InsufficientStock(items.first().productId)
        }
    }

    /**
     * Fallback for reserve: 타임아웃, 서킷 오픈, 5xx 등 발생 시 호출됨.
     * Throwable 파라미터가 반드시 마지막에 있어야 함.
     */
    private fun reserveFallback(orderId: Long, items: List<OrderItem>, e: Throwable): ReserveOutcome {
        if (e is FeignException.Conflict) {
            return ReserveOutcome.InsufficientStock(items.first().productId)
        }
        // 그 외 모든 시스템 오류는 Unavailable 반환하여 Saga 보상 로직 유도
        return ReserveOutcome.Unavailable
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
            // 보상 트랜잭션 실패 시 로깅 (실제 운영 환경에선 재시도 큐나 Dead Letter Queue 검토 필요)
        }
    }
}
