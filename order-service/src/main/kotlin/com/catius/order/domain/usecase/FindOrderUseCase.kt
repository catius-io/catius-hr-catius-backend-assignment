package com.catius.order.domain.usecase

import com.catius.order.domain.usecase.command.OrderResult

fun interface FindOrderUseCase {
    /** 주문이 없으면 [com.catius.order.domain.exception.OrderNotFoundException] 발생. */
    fun findById(id: Long): OrderResult
}
