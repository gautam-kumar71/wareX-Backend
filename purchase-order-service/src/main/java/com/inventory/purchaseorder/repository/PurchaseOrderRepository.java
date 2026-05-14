package com.inventory.purchaseorder.repository;

import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = {"lines"})
    Page<PurchaseOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"lines"})
    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<PurchaseOrder> findBySupplierId(Long supplierId, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<PurchaseOrder> findByWarehouseId(Long warehouseId, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<PurchaseOrder> findByCreatedBy(String createdBy, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    List<PurchaseOrder> findByStatusIn(List<PurchaseOrderStatus> statuses);

    long countBySupplierIdAndStatusIn(Long supplierId, List<PurchaseOrderStatus> statuses);

    List<PurchaseOrder> findBySupplierIdAndStatusIn(Long supplierId, List<PurchaseOrderStatus> statuses);

    @EntityGraph(attributePaths = {"lines"})
    @Query("SELECT po FROM PurchaseOrder po WHERE po.createdAt BETWEEN :from AND :to")
    Page<PurchaseOrder> findByDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);
}
