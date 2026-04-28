package com.catius.order.domain.usecase

import com.catius.order.domain.usecase.command.OrderCommand
import com.catius.order.domain.usecase.command.OrderResult

fun interface CreateOrderUseCase {
    fun create(command: OrderCommand.CreateOrderCommand): OrderResult
}
