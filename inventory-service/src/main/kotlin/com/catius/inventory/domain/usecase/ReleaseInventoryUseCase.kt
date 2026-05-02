package com.catius.inventory.domain.usecase

import com.catius.inventory.domain.usecase.command.InventoryCommand

fun interface ReleaseInventoryUseCase {
    fun release(command: InventoryCommand.ReleaseCommand)
}
