package com.inventory.purchaseorder.repository;

import com.inventory.purchaseorder.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findByPurchaseOrderId(Long purchaseOrderId);

    Optional<PurchaseOrderLine> findByPurchaseOrderIdAndProductId(
            Long purchaseOrderId, Long productId);
}