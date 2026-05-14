package com.inventory.purchaseorder.dto.response;

import com.inventory.purchaseorder.entity.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public record InvoiceResponse(
    Long id,
    String invoiceNumber,
    String orderNumber,
    String purchaseOrderStatus,
    Long supplierId,
    String supplierName,
    BigDecimal amount,
    LocalDate dueDate,
    Invoice.InvoiceStatus status,
    String notes,
    Instant createdAt
) {}
