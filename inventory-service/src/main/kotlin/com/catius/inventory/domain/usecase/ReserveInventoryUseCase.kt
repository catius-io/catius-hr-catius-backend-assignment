package com.catius.inventory.domain.usecase

import com.catius.inventory.domain.usecase.command.InventoryCommand
import com.catius.inventory.domain.usecase.command.ReserveResult

fun interface ReserveInventoryUseCase {
    /** 재고 부족 시 [com.catius.inventory.domain.InsufficientStockException] 발생. */
    fun reserve(command: InventoryCommand.ReserveCommand): ReserveResult
}
