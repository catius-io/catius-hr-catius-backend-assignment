package com.catius.order.infrastructure.persistence.jpa

import com.catius.order.infrastructure.persistence.entity.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderEntity, Long>
