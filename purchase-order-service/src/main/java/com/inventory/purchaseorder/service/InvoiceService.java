package com.inventory.purchaseorder.service;

import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.repository.InvoiceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;

    public Page<Invoice> searchInvoices(String query, Invoice.InvoiceStatus status, Pageable pageable) {
        Specification<Invoice> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query)) {
                String likeQuery = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("invoiceNumber")), likeQuery),
                    cb.like(cb.lower(root.get("supplierName")), likeQuery)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return invoiceRepo.findAll(spec, pageable);
    }
    
    public Invoice createInvoice(Invoice invoice) {
        return invoiceRepo.save(invoice);
    }

    public Invoice getInvoiceByNumber(String invoiceNumber) {
        return invoiceRepo.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceNumber));
    }

    public Invoice getInvoiceByPurchaseOrderId(Long purchaseOrderId) {
        return invoiceRepo.findByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for purchase order: " + purchaseOrderId));
    }

    @Transactional(readOnly = true)
    public boolean isInvoicePaidForPurchaseOrder(Long purchaseOrderId) {
        return getInvoiceByPurchaseOrderId(purchaseOrderId).getStatus() == Invoice.InvoiceStatus.PAID;
    }

    @Transactional
    public Invoice markInvoicePaid(String invoiceNumber, String transactionId) {
        Invoice invoice = getInvoiceByNumber(invoiceNumber);

        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled invoices cannot be marked as paid");
        }
        if (invoice.getPurchaseOrder() != null
                && invoice.getPurchaseOrder().getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Invoices for cancelled purchase orders cannot be marked as paid");
        }

        if (invoice.getStatus() != Invoice.InvoiceStatus.PAID) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
        }

        if (transactionId != null && !transactionId.isBlank()) {
            String auditNote = "Payment settled via transaction " + transactionId;
            String existingNotes = invoice.getNotes();
            if (existingNotes == null || existingNotes.isBlank()) {
                invoice.setNotes(auditNote);
            } else if (!Objects.equals(existingNotes, auditNote) && !existingNotes.contains(auditNote)) {
                invoice.setNotes(existingNotes + " | " + auditNote);
            }
        }

        invoiceRepo.save(invoice);
        return getInvoiceByNumber(invoiceNumber);
    }

    @Transactional
    public void cancelInvoiceForPurchaseOrder(Long purchaseOrderId, String cancelReason) {
        invoiceRepo.findByPurchaseOrderId(purchaseOrderId).ifPresent(invoice -> {
            if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
                return;
            }

            invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);

            String auditNote = StringUtils.hasText(cancelReason)
                    ? "Invoice closed because the purchase order was cancelled: " + cancelReason.trim()
                    : "Invoice closed because the purchase order was cancelled.";
            String existingNotes = invoice.getNotes();
            if (!StringUtils.hasText(existingNotes)) {
                invoice.setNotes(auditNote);
            } else if (!existingNotes.contains(auditNote)) {
                invoice.setNotes(existingNotes + " | " + auditNote);
            }

            invoiceRepo.save(invoice);
        });
    }

    @Transactional
    public Invoice cancelPaidInvoice(String invoiceNumber, String transactionId, String cancelReason) {
        Invoice invoice = getInvoiceByNumber(invoiceNumber);
        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);

        StringBuilder audit = new StringBuilder("Invoice closed because payment ");
        if (transactionId != null && !transactionId.isBlank()) {
            audit.append(transactionId).append(' ');
        }
        audit.append("was cancelled");
        if (StringUtils.hasText(cancelReason)) {
            audit.append(": ").append(cancelReason.trim());
        } else {
            audit.append('.');
        }

        String auditNote = audit.toString();
        String existingNotes = invoice.getNotes();
        if (!StringUtils.hasText(existingNotes)) {
            invoice.setNotes(auditNote);
        } else if (!existingNotes.contains(auditNote)) {
            invoice.setNotes(existingNotes + " | " + auditNote);
        }

        return invoiceRepo.save(invoice);
    }

    @Transactional
    public void generateInvoiceFromOrder(com.inventory.purchaseorder.entity.PurchaseOrder order) {
        // Idempotency check: don't create duplicate invoices for the same PO
        if (invoiceRepo.existsByPurchaseOrderId(order.getId())) {
            return;
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        String invNumber = "INV-" + todayUtc.toString().replace("-", "")
                + "-" + java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invNumber)
                .purchaseOrder(order)
                .supplierId(order.getSupplierId())
                .supplierName(order.getSupplierName())
                .amount(order.getTotalAmount())
                .dueDate(todayUtc.plusDays(30))
                .status(Invoice.InvoiceStatus.PENDING)
                .notes("Auto-generated from PO: " + order.getOrderNumber())
                .build();

        invoiceRepo.save(invoice);
    }
}
