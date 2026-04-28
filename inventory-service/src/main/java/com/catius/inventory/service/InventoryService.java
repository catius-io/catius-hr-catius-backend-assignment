package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.domain.Inventory;
import com.catius.inventory.exception.InsufficientStockException;
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
        int updated = inventoryRepository.reserve(request.productId(), request.quantity());
        if (updated == 0) {
            Inventory inventory = inventoryRepository.findByProductId(request.productId())
                    .orElseThrow(() -> new StockNotFoundException(request.productId()));
            throw new InsufficientStockException(request.productId(), inventory.getQuantity(), request.quantity());
        }
        return inventoryRepository.findByProductId(request.productId())
                .map(InventoryResponse::from)
                .orElseThrow(() -> new StockNotFoundException(request.productId()));
    }

    @Transactional
    public InventoryResponse release(InventoryRequest request) {
        int updated = inventoryRepository.release(request.productId(), request.quantity());
        if (updated == 0) {
            throw new StockNotFoundException(request.productId());
        }
        return inventoryRepository.findByProductId(request.productId())
                .map(InventoryResponse::from)
                .orElseThrow(() -> new StockNotFoundException(request.productId()));
    }
}
