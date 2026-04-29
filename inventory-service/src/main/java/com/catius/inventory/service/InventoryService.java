package com.catius.inventory.service;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.exception.InsufficientStockException;
import com.catius.inventory.domain.exception.ProductNotFoundException;
import com.catius.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository repository;

    public Inventory getInventory(Long productId) {
        return repository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /**
     * 조건부 atomic UPDATE 로 재고를 차감한다.
     * affected rows == 1: 성공.
     * affected rows == 0: 상품이 없거나 재고가 부족함 — 한 번의 SELECT 로 원인을 가려 적절한 예외를 던진다.
     */
    @Transactional
    public void reserve(Long productId, int quantity) {
        validatePositive(quantity);

        int updated = repository.decreaseStock(productId, quantity);
        if (updated == 1) {
            return;
        }

        Inventory current = repository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        throw new InsufficientStockException(productId, current.getStock(), quantity);
    }

    @Transactional
    public void release(Long productId, int quantity) {
        validatePositive(quantity);

        int updated = repository.increaseStock(productId, quantity);
        if (updated == 0) {
            throw new ProductNotFoundException(productId);
        }
    }

    private static void validatePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }
}