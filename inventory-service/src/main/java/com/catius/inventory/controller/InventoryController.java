package com.catius.inventory.controller;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public InventoryResponse getStock(@PathVariable Long productId) {
        return inventoryService.findByProductId(productId);
    }

    @PostMapping("/reserve")
    public InventoryResponse reserve(@RequestBody InventoryRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/release")
    public InventoryResponse release(@RequestBody InventoryRequest request) {
        return inventoryService.release(request);
    }
}