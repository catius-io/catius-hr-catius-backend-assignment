package com.catius.inventory.repository.entity

import com.catius.inventory.domain.MovementType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "stock_movements",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_stock_movement_order_product_type",
            columnNames = ["order_id", "product_id", "type"],
        ),
    ],
)
class StockMovementEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0,

    @Column(name = "product_id", nullable = false)
    var productId: Long = 0,

    @Column(nullable = false)
    var quantity: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: MovementType = MovementType.RESERVE,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)
