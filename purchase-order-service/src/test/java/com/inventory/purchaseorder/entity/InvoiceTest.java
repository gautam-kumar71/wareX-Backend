package com.inventory.purchaseorder.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    @Test
    void onCreate_setsDefaults() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-1")
                .purchaseOrder(PurchaseOrder.builder().orderNumber("PO-1").build())
                .supplierId(1L)
                .supplierName("Supplier")
                .amount(BigDecimal.TEN)
                .dueDate(LocalDate.parse("2026-01-10"))
                .build();

        invoice.onCreate();

        assertThat(invoice.getCreatedAt()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PENDING);
    }
}
