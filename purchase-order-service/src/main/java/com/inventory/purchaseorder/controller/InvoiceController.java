package com.inventory.purchaseorder.controller;

import com.inventory.purchaseorder.dto.response.ApiResponse;
import com.inventory.purchaseorder.dto.response.InvoiceResponse;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.service.InvoiceService;
import com.inventory.purchaseorder.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Financial tracking for purchase orders")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Search and list invoices",
            description = "Supports partial match on invoice number/supplier and status filtering.")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Invoice.InvoiceStatus status,
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {

        Page<InvoiceResponse> page = invoiceService.searchInvoices(query, status, pageable)
            .map(inv -> new InvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getPurchaseOrder().getOrderNumber(),
                inv.getPurchaseOrder().getStatus().name(),
                inv.getSupplierId(),
                inv.getSupplierName(),
                inv.getAmount(),
                inv.getDueDate(),
                inv.getStatus(),
                inv.getNotes(),
                inv.getCreatedAt()
            ));

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/number/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Get invoice by invoice number")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByInvoiceNumber(
            @PathVariable String invoiceNumber) {
        Invoice inv = invoiceService.getInvoiceByNumber(invoiceNumber);

        InvoiceResponse response = new InvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getPurchaseOrder().getOrderNumber(),
                inv.getPurchaseOrder().getStatus().name(),
                inv.getSupplierId(),
                inv.getSupplierName(),
                inv.getAmount(),
                inv.getDueDate(),
                inv.getStatus(),
                inv.getNotes(),
                inv.getCreatedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/purchase-order/{purchaseOrderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get invoice by purchase order ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByPurchaseOrderId(
            @PathVariable Long purchaseOrderId) {
        Invoice inv = invoiceService.getInvoiceByPurchaseOrderId(purchaseOrderId);

        InvoiceResponse response = new InvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getPurchaseOrder().getOrderNumber(),
                inv.getPurchaseOrder().getStatus().name(),
                inv.getSupplierId(),
                inv.getSupplierName(),
                inv.getAmount(),
                inv.getDueDate(),
                inv.getStatus(),
                inv.getNotes(),
                inv.getCreatedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/number/{invoiceNumber}/paid")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Mark invoice as paid")
    public ResponseEntity<ApiResponse<InvoiceResponse>> markInvoicePaid(
            @PathVariable String invoiceNumber,
            @RequestParam(required = false) String transactionId) {
        Invoice inv = invoiceService.markInvoicePaid(invoiceNumber, transactionId);

        InvoiceResponse response = new InvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getPurchaseOrder().getOrderNumber(),
                inv.getPurchaseOrder().getStatus().name(),
                inv.getSupplierId(),
                inv.getSupplierName(),
                inv.getAmount(),
                inv.getDueDate(),
                inv.getStatus(),
                inv.getNotes(),
                inv.getCreatedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response, "Invoice marked as paid"));
    }

    @PostMapping("/number/{invoiceNumber}/payment-cancelled")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Cancel a paid invoice and its purchase order after payment cancellation")
    public ResponseEntity<ApiResponse<Void>> cancelAfterPaymentCancellation(
            @PathVariable String invoiceNumber,
            @RequestParam String transactionId,
            @RequestParam String reason,
            @RequestHeader("X-User-Id") String userId) {
        purchaseOrderService.cancelOrderForPayment(invoiceNumber, transactionId, reason, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Purchase order and invoice cancelled after payment cancellation"));
    }
}
