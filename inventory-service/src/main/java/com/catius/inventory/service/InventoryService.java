package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.domain.Inventory;
import com.catius.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public InventoryResponse findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found: " + productId));
    }

    @Transactional
    public InventoryResponse reserve(InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found: " + request.productId()));
        inventory.deduct(inventory.getQuantity());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse release(InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found: " + request.productId()));
        inventory.restore(inventory.getQuantity());
        return InventoryResponse.from(inventory);
    }
}
