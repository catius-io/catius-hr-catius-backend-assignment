package com.catius.order.service

import com.catius.order.domain.Order
import com.catius.order.domain.OrderDomainEvent
import com.catius.order.domain.OrderItem
import com.catius.order.domain.exception.OrderNotFoundException
import com.catius.order.domain.port.InventoryClient
import com.catius.order.domain.port.OrderEventPublisher
import com.catius.order.domain.port.OrderRepository
import com.catius.order.domain.port.ReserveOutcome
import com.catius.order.domain.usecase.CreateOrderUseCase
import com.catius.order.domain.usecase.FindOrderUseCase
import com.catius.order.domain.usecase.command.OrderCommand
import com.catius.order.domain.usecase.command.OrderResult
import com.catius.order.service.mapper.OrderResultMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val inventoryClient: InventoryClient,
    private val orderEventPublisher: OrderEventPublisher,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) : CreateOrderUseCase, FindOrderUseCase {

    override fun create(command: OrderCommand.CreateOrderCommand): OrderResult {
        val now = Instant.now(clock)
        val order = Order.create(
            customerId = command.customerId,
            items = command.items.map { OrderItem(productId = it.productId, quantity = it.quantity) },
            now = now,
        )

        // 1. PENDING 상태로 주문 생성 및 커밋 (ID 확정)
        val pendingOrder = transactionTemplate.execute {
            orderRepository.save(order)
        } ?: throw RuntimeException("Failed to save pending order")

        val orderId = pendingOrder.id!!

        // 2. 재고 예약 시도 (외부 시스템 호출이므로 트랜잭션 외부에서 수행)
        val outcome = inventoryClient.reserve(orderId, pendingOrder.items)

        // 3. 예약 결과에 따라 주문 상태 업데이트 및 확정 이벤트 발행
        val finalOrder = transactionTemplate.execute {
            val freshOrder = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)
            
            val updated = when (outcome) {
                is ReserveOutcome.Success -> {
                    val confirmed = freshOrder.confirm(Instant.now(clock))
                    val saved = orderRepository.save(confirmed)
                    orderEventPublisher.publish(
                        OrderDomainEvent.Confirmed(
                            orderId = orderId,
                            customerId = saved.customerId,
                            items = saved.items,
                            occurredAt = saved.updatedAt,
                        ),
                    )
                    saved
                }
                is ReserveOutcome.InsufficientStock -> {
                    val failed = freshOrder.fail(Instant.now(clock))
                    orderRepository.save(failed)
                }
                is ReserveOutcome.Unavailable -> {
                    // 보상 트랜잭션: 응답이 모호할 경우 혹시 모를 차감분을 복원 시도
                    inventoryClient.release(orderId, freshOrder.items)
                    val failed = freshOrder.fail(Instant.now(clock))
                    orderRepository.save(failed)
                }
            }
            updated
        } ?: throw RuntimeException("Failed to finalize order")

        return OrderResultMapper.toResult(finalOrder)
    }

    override fun findById(id: Long): OrderResult {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        return OrderResultMapper.toResult(order)
    }
}
