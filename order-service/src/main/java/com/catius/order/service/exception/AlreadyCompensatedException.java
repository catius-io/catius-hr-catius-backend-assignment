package com.catius.order.service.exception;

/**
 * inventory의 tombstone에 의해 차단된 늦은 reserve — release-before-reserve race.
 * Saga 측에서는 explicit failure로 처리 (이미 보상이 진행됐음을 의미).
 */
public class AlreadyCompensatedException extends RuntimeException {

    public AlreadyCompensatedException(String message) {
        super(message);
    }
}
