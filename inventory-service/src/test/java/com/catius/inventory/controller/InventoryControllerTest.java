package com.catius.inventory.controller;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

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
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));

        mockMvc.perform(get("/api/v1/inventory/PRODUCT-999"))
                .andExpect(status().isNotFound());
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
    @DisplayName("재고 예약 - 없는 상품 → 404")
    void reserve_404() throws Exception {
        given(inventoryService.reserve(any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-999", 1))))
                .andExpect(status().isNotFound());
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
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryRequest("PRODUCT-999", 1))))
                .andExpect(status().isNotFound());
    }
}
