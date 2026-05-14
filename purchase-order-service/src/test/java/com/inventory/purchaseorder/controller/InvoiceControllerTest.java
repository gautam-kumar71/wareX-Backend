package com.inventory.purchaseorder.controller;

import com.inventory.purchaseorder.dto.response.InvoiceResponse;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.service.InvoiceService;
import com.inventory.purchaseorder.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class InvoiceControllerTest {

    private final InvoiceService service = mock(InvoiceService.class);
    private final PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
    private final InvoiceController controller = new InvoiceController(service, purchaseOrderService);

    @Test
    void search_getByNumber_getByPurchaseOrder_markPaid_mapInvoiceToResponse() {
        var pageable = PageRequest.of(0, 20);
        Invoice invoice = invoice();
        given(service.searchInvoices("INV", Invoice.InvoiceStatus.PENDING, pageable)).willReturn(new PageImpl<>(List.of(invoice)));
        given(service.getInvoiceByNumber("INV-1")).willReturn(invoice);
        given(service.getInvoiceByPurchaseOrderId(1L)).willReturn(invoice);
        given(service.markInvoicePaid("INV-1", "TXN")).willReturn(invoice);

        assertThat(controller.search("INV", Invoice.InvoiceStatus.PENDING, pageable).getBody().data().getContent().get(0).invoiceNumber()).isEqualTo("INV-1");
        assertThat(controller.getByInvoiceNumber("INV-1").getBody().data().orderNumber()).isEqualTo("PO-1");
        assertThat(controller.getByPurchaseOrderId(1L).getBody().data().supplierName()).isEqualTo("Supplier");
        assertThat(controller.markInvoicePaid("INV-1", "TXN").getBody().message()).contains("paid");
    }

    @Test
    void cancelAfterPaymentCancellation_delegatesToPurchaseOrderService() {
        assertThat(controller.cancelAfterPaymentCancellation("INV-1", "TXN-1", "reason", "u1")
                .getBody().message()).contains("cancelled");
        org.mockito.Mockito.verify(purchaseOrderService)
                .cancelOrderForPayment("INV-1", "TXN-1", "reason", "u1");
    }

    private Invoice invoice() {
        PurchaseOrder po = PurchaseOrder.builder().orderNumber("PO-1").status(PurchaseOrderStatus.APPROVED).build();
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
