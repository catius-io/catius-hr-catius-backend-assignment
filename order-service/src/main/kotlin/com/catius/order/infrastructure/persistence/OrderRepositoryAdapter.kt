package com.catius.order.infrastructure.persistence

import com.catius.order.domain.Order
import com.catius.order.domain.port.OrderRepository
import com.catius.order.infrastructure.persistence.jpa.OrderJpaRepository
import com.catius.order.infrastructure.persistence.mapper.OrderMapper
import org.springframework.stereotype.Component

@Component
class OrderRepositoryAdapter(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {

    override fun save(order: Order): Order {
        return if (order.id == null) {
            val entity = OrderMapper.toNewEntity(order)
            OrderMapper.toDomain(orderJpaRepository.save(entity))
        } else {
            val existing = orderJpaRepository.findById(order.id).orElseThrow()
            OrderMapper.applyTo(existing, order)
            OrderMapper.toDomain(orderJpaRepository.save(existing))
        }
    }

    override fun findById(id: Long): Order? {
        return orderJpaRepository.findById(id).map { OrderMapper.toDomain(it) }.orElse(null)
    }
}
