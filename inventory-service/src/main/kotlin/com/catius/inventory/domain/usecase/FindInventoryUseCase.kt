package com.catius.inventory.domain.usecase

import com.catius.inventory.domain.usecase.command.InventoryResult

fun interface FindInventoryUseCase {
    /** 재고 정보가 없으면 [com.catius.inventory.domain.exception.InventoryNotFoundException] 발생. */
    fun findByProductId(productId: Long): InventoryResult
}
