package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.domain.Inventory;
import com.catius.inventory.exception.StockNotFoundException;
import com.catius.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public InventoryResponse findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new StockNotFoundException(productId));
    }

    @Transactional
    public InventoryResponse reserve(InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new StockNotFoundException(request.productId()));
        inventory.deduct(request.quantity());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse release(InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new StockNotFoundException(request.productId()));
        inventory.restore(request.quantity());
        return InventoryResponse.from(inventory);
    }
}
