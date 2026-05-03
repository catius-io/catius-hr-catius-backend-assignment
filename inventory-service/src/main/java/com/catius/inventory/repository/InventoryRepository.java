package com.catius.inventory.repository;

import com.catius.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Modifying
    @Query(value = """
            UPDATE inventory
               SET quantity = quantity - :quantity
             WHERE product_id = :productId
               AND quantity >= :quantity
            """, nativeQuery = true)
    int decrementIfSufficient(@Param("productId") long productId,
                              @Param("quantity") int quantity);

    @Modifying
    @Query(value = """
            UPDATE inventory
               SET quantity = quantity + :quantity
             WHERE product_id = :productId
            """, nativeQuery = true)
    int increment(@Param("productId") long productId,
                  @Param("quantity") int quantity);
}
