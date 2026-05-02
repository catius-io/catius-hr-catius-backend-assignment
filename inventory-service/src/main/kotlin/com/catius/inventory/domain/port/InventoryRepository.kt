package com.catius.inventory.domain.port

import com.catius.inventory.domain.Inventory

interface InventoryRepository {
    fun save(inventory: Inventory): Inventory

    /** 락 없이 단순 조회 (find usecase 용). */
    fun findByProductId(productId: Long): Inventory?

    /** 비관적 쓰기 락으로 조회. 차감/복원 흐름에서 사용. */
    fun findByProductIdForUpdate(productId: Long): Inventory?
}
