package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
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
        return null;
    }

    @Transactional
    public InventoryResponse reserve(InventoryRequest request) {
        return null;
    }

    @Transactional
    public InventoryResponse release(InventoryRequest request) {
        return null;
    }
}
