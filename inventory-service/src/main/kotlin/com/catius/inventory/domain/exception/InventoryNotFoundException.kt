package com.catius.inventory.domain.exception

class InventoryNotFoundException(val productId: Long) :
    RuntimeException("재고 정보를 찾을 수 없습니다: productId=$productId")
