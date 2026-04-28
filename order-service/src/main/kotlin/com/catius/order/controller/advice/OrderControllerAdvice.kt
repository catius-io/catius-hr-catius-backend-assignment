package com.catius.order.controller.advice

import com.catius.order.domain.exception.InsufficientStockException
import com.catius.order.domain.exception.OrderNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class OrderControllerAdvice {

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleNotFound(e: OrderNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(e.message ?: "Order not found"))
    }

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(e: InsufficientStockException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(e.message ?: "Insufficient stock"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(e.message ?: "Bad request"))
    }

    data class ErrorResponse(val message: String)
}
