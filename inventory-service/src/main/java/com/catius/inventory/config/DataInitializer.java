package com.catius.inventory.config;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Inventory> seeds = List.of(
                Inventory.create("PRODUCT-001", "상품 001", 100),
                Inventory.create("PRODUCT-002", "상품 002", 100),
                Inventory.create("PRODUCT-003", "상품 003", 100),
                Inventory.create("PRODUCT-004", "상품 004", 100),
                Inventory.create("PRODUCT-005", "상품 005", 100)
        );

        for (Inventory seed : seeds) {
            if (inventoryRepository.findByProductId(seed.getProductId()).isEmpty()) {
                inventoryRepository.save(seed);
                log.info("[DataInitializer] 재고 초기화: productId={}, qty={}", seed.getProductId(), seed.getQuantity());
            }
        }
    }
}
