package com.catius.order.domain;

/**
 * 주문 Saga 의 상태.
 * 전이 그래프는 {@link #canTransition(OrderStatus)} 참조; 종착 상태는 {@link #isTerminal()}.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    FAILED,
    COMPENSATED;

    public boolean canTransition(OrderStatus next) {
        return switch (this) {
            case PENDING                -> next == CONFIRMED || next == FAILED;
            case CONFIRMED              -> next == COMPENSATED;
            case FAILED, COMPENSATED    -> false;
        };
    }

    public boolean isTerminal() {
        return this == FAILED || this == COMPENSATED;
    }
}
