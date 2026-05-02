package com.catius.inventory.infrastructure.persistence.jpa

import com.catius.inventory.infrastructure.persistence.entity.InventoryEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryJpaRepository : JpaRepository<InventoryEntity, Long> {

    fun findByProductId(productId: Long): InventoryEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): InventoryEntity?
}
