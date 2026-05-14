package com.inventory.warehouse.repository;

import com.inventory.warehouse.entity.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    Optional<StockLevel> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    List<StockLevel> findByWarehouseId(Long warehouseId);

    List<StockLevel> findByProductId(Long productId);

    boolean existsByWarehouseIdAndProductId(Long warehouseId, Long productId);

    // PESSIMISTIC_WRITE lock for stock transfer — debit side
    // Prevents another transaction from reading stale data during the debit+credit pair
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.productId = :productId")
    Optional<StockLevel> findByWarehouseIdAndProductIdForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId);

    // All stock levels below their reorder point — used by Alert Service via API
    @Query("""
            SELECT s FROM StockLevel s
            WHERE s.reorderPoint > 0
              AND s.quantity <= s.reorderPoint
              AND (s.maxCapacity IS NULL OR s.quantity < s.maxCapacity)
            """)
    List<StockLevel> findAllLowStock();

    // All stock across all warehouses for a product
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockLevel s WHERE s.productId = :productId")
    int sumQuantityByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockLevel s WHERE s.warehouse.id = :warehouseId")
    int sumQuantityByWarehouseId(@Param("warehouseId") Long warehouseId);
}
