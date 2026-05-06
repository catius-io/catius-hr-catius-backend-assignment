package com.catius.inventory.controller;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.exception.InsufficientStockException;
import com.catius.inventory.exception.StockNotFoundException;
import com.catius.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    // ── GET /api/v1/inventory/{productId} ────────────────────────

    @Test
    @DisplayName("재고 조회 - 200 OK")
    void getStock_200() throws Exception {
        given(inventoryService.findByProductId("PRODUCT-001"))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 10));

        mockMvc.perform(get("/api/v1/inventory/PRODUCT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PRODUCT-001"))
                .andExpect(jsonPath("$.productName").value("테스트 상품"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    @DisplayName("재고 조회 - 없는 상품 → 404")
    void getStock_404() throws Exception {
        given(inventoryService.findByProductId("PRODUCT-999"))
                .willThrow(new StockNotFoundException("PRODUCT-999"));

        mockMvc.perform(get("/api/v1/inventory/PRODUCT-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Stock not found: PRODUCT-999"))
                .andExpect(jsonPath("$.path").value("/api/v1/inventory/PRODUCT-999"));
    }

    // ── POST /api/v1/inventory/reserve ───────────────────────────

    @Test
    @DisplayName("재고 예약 - 200 OK")
    void reserve_200() throws Exception {
        given(inventoryService.reserve(any()))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 0));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-001", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PRODUCT-001"))
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    @DisplayName("재고 예약 - 재고 부족 → 409")
    void reserve_409() throws Exception {
        given(inventoryService.reserve(any()))
                .willThrow(new InsufficientStockException("PRODUCT-001", 3, 10));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-001", 10))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Insufficient stock: productId=PRODUCT-001, current=3, requested=10"))
                .andExpect(jsonPath("$.path").value("/api/v1/inventory/reserve"));
    }

    @Test
    @DisplayName("재고 예약 - 없는 상품 → 404")
    void reserve_404() throws Exception {
        given(inventoryService.reserve(any()))
                .willThrow(new StockNotFoundException("PRODUCT-999"));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-999", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Stock not found: PRODUCT-999"));
    }

    // ── POST /api/v1/inventory/release ───────────────────────────

    @Test
    @DisplayName("재고 복구 - 200 OK")
    void release_200() throws Exception {
        given(inventoryService.release(any()))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 10));

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-001", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PRODUCT-001"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    @DisplayName("재고 복구 - 없는 상품 → 404")
    void release_404() throws Exception {
        given(inventoryService.release(any()))
                .willThrow(new StockNotFoundException("PRODUCT-999"));

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-999", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Stock not found: PRODUCT-999"));
    }
}
