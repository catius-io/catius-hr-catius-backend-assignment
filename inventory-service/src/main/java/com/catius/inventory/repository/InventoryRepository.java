package com.catius.inventory.repository;

import com.catius.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(String productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.quantity = i.quantity - :quantity,
                   i.updatedAt = current_timestamp
             where i.productId = :productId
               and i.quantity >= :quantity
            """)
    int reserve(@Param("productId") String productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.quantity = i.quantity + :quantity,
                   i.updatedAt = current_timestamp
             where i.productId = :productId
            """)
    int release(@Param("productId") String productId, @Param("quantity") int quantity);
}
