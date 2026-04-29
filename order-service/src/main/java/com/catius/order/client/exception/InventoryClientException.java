package com.catius.order.client.exception;

/**
 * inventory-service 호출에서 발생한 모든 실패의 부모 타입.
 * Saga 흐름은 이 타입(또는 하위) 을 catch 해 보상 트리거 여부를 결정한다.
 */
public class InventoryClientException extends RuntimeException {

    public InventoryClientException(String message) {
        super(message);
    }

    public InventoryClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
