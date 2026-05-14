package com.inventory.stockmovement.repository;

import com.inventory.stockmovement.entity.StockMovement;
import com.inventory.stockmovement.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Used by idempotency check — cheaper than catching DataIntegrityViolationException
    boolean existsByEventId(String eventId);

    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    Page<StockMovement> findByWarehouseId(Long warehouseId, Pageable pageable);

    Page<StockMovement> findByProductIdAndWarehouseId(Long productId, Long warehouseId,
                                                      Pageable pageable);

    Page<StockMovement> findByMovementType(MovementType type, Pageable pageable);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.occurredAt BETWEEN :from AND :to")
    Page<StockMovement> findByDateRange(@Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);

    @Query("SELECT sm FROM StockMovement sm " +
            "WHERE sm.productId = :productId " +
            "AND sm.occurredAt BETWEEN :from AND :to " +
            "ORDER BY sm.occurredAt ASC")
    List<StockMovement> findByProductIdAndDateRange(@Param("productId") Long productId,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to);

    Page<StockMovement> findByReferenceIdAndReferenceType(String referenceId,
                                                          String referenceType,
                                                          Pageable pageable);
}