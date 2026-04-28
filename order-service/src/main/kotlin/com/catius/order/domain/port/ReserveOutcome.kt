package com.catius.order.domain.port

sealed interface ReserveOutcome {
    /** 차감 성공. */
    data object Success : ReserveOutcome

    /** 재고 부족이 명확히 확인됨 — 보상(release) 호출 불필요. */
    data class InsufficientStock(val productId: Long) : ReserveOutcome

    /** 응답이 모호함 (타임아웃, CB open, 5xx 등) — 보상 호출이 필요할 수 있음. */
    data object Unavailable : ReserveOutcome
}
