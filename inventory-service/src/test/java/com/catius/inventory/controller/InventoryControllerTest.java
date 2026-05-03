package com.catius.inventory.controller;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.Reservation;
import com.catius.inventory.service.InventoryReservationService;
import com.catius.inventory.service.ReleaseOutcome;
import com.catius.inventory.service.exception.AlreadyCompensatedException;
import com.catius.inventory.service.exception.InsufficientStockException;
import com.catius.inventory.service.exception.ProductNotFoundException;
import com.catius.inventory.service.exception.ReservationConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    InventoryReservationService service;

    @Test
    void getInventory_returns200WithBody() throws Exception {
        when(service.getInventory(1001L)).thenReturn(Inventory.of(1001L, 50));

        mockMvc.perform(get("/api/v1/inventory/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1001))
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void getInventory_returns404WhenProductNotFound() throws Exception {
        when(service.getInventory(9999L)).thenThrow(new ProductNotFoundException(9999L));

        mockMvc.perform(get("/api/v1/inventory/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getInventory_returns400WhenProductIdIsZero() throws Exception {
        when(service.getInventory(0L)).thenThrow(new IllegalArgumentException("productId must be positive: 0"));

        mockMvc.perform(get("/api/v1/inventory/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void reserve_returns200WithReservation() throws Exception {
        when(service.reserve(anyString(), anyLong(), anyInt()))
                .thenReturn(Reservation.reserved("order-1", 1001L, 3));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L, "quantity", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.productId").value(1001))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.state").value("RESERVED"));
    }

    @Test
    void reserve_returns409OnInsufficientStock() throws Exception {
        when(service.reserve(anyString(), anyLong(), anyInt()))
                .thenThrow(new InsufficientStockException(1001L, 5));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L, "quantity", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void reserve_returns409OnAlreadyCompensated() throws Exception {
        when(service.reserve(anyString(), anyLong(), anyInt()))
                .thenThrow(new AlreadyCompensatedException("order-1", 1001L));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L, "quantity", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_COMPENSATED"));
    }

    @Test
    void reserve_returns409OnPayloadDrift() throws Exception {
        when(service.reserve(anyString(), anyLong(), anyInt()))
                .thenThrow(new ReservationConflictException("order-1", 1001L, 3, 5));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L, "quantity", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test
    void reserve_returns400WhenOrderIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("productId", 1001L, "quantity", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reserve_returns400WhenQuantityNonPositive() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L, "quantity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reserve_returns400WhenProductIdNonPositive() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 0L, "quantity", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reserve_returns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void release_returns200Released() throws Exception {
        when(service.release(anyString(), anyLong())).thenReturn(ReleaseOutcome.RELEASED);

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RELEASED"));
    }

    @Test
    void release_returns200AlreadyReleased() throws Exception {
        when(service.release(anyString(), anyLong())).thenReturn(ReleaseOutcome.ALREADY_RELEASED);

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_RELEASED"));
    }

    @Test
    void release_returns200Tombstoned() throws Exception {
        when(service.release(anyString(), anyLong())).thenReturn(ReleaseOutcome.TOMBSTONED);

        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", "order-1", "productId", 1001L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("TOMBSTONED"));
    }

    @Test
    void release_returns400WhenOrderIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("productId", 1001L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
