package com.catius.inventory.service;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.exception.ProductNotFoundException;
import com.catius.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository repository;

    @Transactional(readOnly = true)
    public Inventory getInventory(Long productId) {
        return repository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inventory = repository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        inventory.reserve(quantity);
    }

    @Transactional
    public void release(Long productId, int quantity) {
        Inventory inventory = repository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        inventory.release(quantity);
    }
}
