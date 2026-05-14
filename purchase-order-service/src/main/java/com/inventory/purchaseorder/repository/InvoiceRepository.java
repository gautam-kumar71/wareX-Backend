package com.inventory.purchaseorder.repository;

import com.inventory.purchaseorder.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
    @Override
    @EntityGraph(attributePaths = {"purchaseOrder"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"purchaseOrder"})
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    @EntityGraph(attributePaths = {"purchaseOrder"})
    Optional<Invoice> findByPurchaseOrderId(Long purchaseOrderId);
    boolean existsByInvoiceNumber(String invoiceNumber);
    boolean existsByPurchaseOrderId(Long purchaseOrderId);
    List<Invoice> findBySupplierIdAndStatusIn(Long supplierId, List<Invoice.InvoiceStatus> statuses);
}
