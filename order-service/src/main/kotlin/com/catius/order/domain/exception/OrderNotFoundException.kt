package com.catius.order.domain.exception

class OrderNotFoundException(val id: Long) :
    RuntimeException("주문을 찾을 수 없습니다: id=$id")
