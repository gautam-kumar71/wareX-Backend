package com.inventory.purchaseorder.service;

import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.repository.InvoiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepo;

    @InjectMocks
    private InvoiceService service;

    @Test
    void searchInvoices_delegatesToRepository() {
        var pageable = PageRequest.of(0, 20);
        given(invoiceRepo.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .willReturn(new PageImpl<>(List.of(invoice())));

        assertThat(service.searchInvoices("INV", Invoice.InvoiceStatus.PENDING, pageable).getContent()).hasSize(1);
    }

    @Test
    void createAndLookupMethods_delegateOrThrow() {
        Invoice invoice = invoice();
        given(invoiceRepo.save(invoice)).willReturn(invoice);
        given(invoiceRepo.findByInvoiceNumber("INV-1")).willReturn(Optional.of(invoice));
        given(invoiceRepo.findByPurchaseOrderId(1L)).willReturn(Optional.of(invoice));

        assertThat(service.createInvoice(invoice)).isSameAs(invoice);
        assertThat(service.getInvoiceByNumber("INV-1")).isSameAs(invoice);
        assertThat(service.getInvoiceByPurchaseOrderId(1L)).isSameAs(invoice);

        given(invoiceRepo.findByInvoiceNumber("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getInvoiceByNumber("missing")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void markInvoicePaid_updatesStatusAndNotes() {
        Invoice invoice = invoice();
        invoice.setStatus(Invoice.InvoiceStatus.PENDING);
        invoice.setNotes("Existing");
        given(invoiceRepo.findByInvoiceNumber("INV-1")).willReturn(Optional.of(invoice));
        given(invoiceRepo.save(invoice)).willReturn(invoice);

        Invoice updated = service.markInvoicePaid("INV-1", "TXN-1");

        assertThat(updated.getStatus()).isEqualTo(Invoice.InvoiceStatus.PAID);
        assertThat(updated.getNotes()).contains("TXN-1");
    }

    @Test
    void markInvoicePaid_cancelledInvoiceThrows() {
        Invoice invoice = invoice();
        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
        given(invoiceRepo.findByInvoiceNumber("INV-1")).willReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.markInvoicePaid("INV-1", "TXN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancelled");
    }

    @Test
    void cancelInvoiceForPurchaseOrder_handlesPendingAndPaidInvoices() {
        Invoice invoice = invoice();
        invoice.setStatus(Invoice.InvoiceStatus.PENDING);
        given(invoiceRepo.findByPurchaseOrderId(1L)).willReturn(Optional.of(invoice));

        service.cancelInvoiceForPurchaseOrder(1L, "duplicate");

        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.CANCELLED);
        assertThat(invoice.getNotes()).contains("duplicate");
        verify(invoiceRepo).save(invoice);

        Invoice paid = invoice();
        paid.setStatus(Invoice.InvoiceStatus.PAID);
        given(invoiceRepo.findByPurchaseOrderId(2L)).willReturn(Optional.of(paid));
        service.cancelInvoiceForPurchaseOrder(2L, "ignored");
        verify(invoiceRepo, never()).save(paid);
    }

    @Test
    void generateInvoiceFromOrder_isIdempotent() {
        PurchaseOrder order = PurchaseOrder.builder()
                .id(1L)
                .orderNumber("PO-1")
                .supplierId(5L)
                .supplierName("Supplier")
                .totalAmount(BigDecimal.TEN)
                .build();
        given(invoiceRepo.existsByPurchaseOrderId(1L)).willReturn(false, true);

        service.generateInvoiceFromOrder(order);
        service.generateInvoiceFromOrder(order);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertThat(captor.getValue().getInvoiceNumber()).startsWith("INV-");
        assertThat(captor.getValue().getNotes()).contains("PO-1");
    }

    private Invoice invoice() {
        PurchaseOrder po = PurchaseOrder.builder().id(1L).orderNumber("PO-1").build();
        return Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-1")
                .purchaseOrder(po)
                .supplierId(5L)
                .supplierName("Supplier")
                .amount(BigDecimal.TEN)
                .dueDate(LocalDate.parse("2026-01-10"))
                .status(Invoice.InvoiceStatus.PENDING)
                .notes("notes")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
