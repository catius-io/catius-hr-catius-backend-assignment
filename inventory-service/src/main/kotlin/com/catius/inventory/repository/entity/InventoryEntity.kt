package com.catius.inventory.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "inventory")
class InventoryEntity(
    @Id
    @Column(name = "product_id")
    var productId: Long = 0,

    @Column(name = "available_quantity", nullable = false)
    var availableQuantity: Int = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
)
