package com.inventory.stockmovement.feign;

import com.inventory.stockmovement.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "purchase-order-service", url = "${app.services.purchase-order-service.url}")
public interface PurchaseOrderClient {

    @GetMapping("/api/v1/invoices/purchase-order/{purchaseOrderId}")
    ApiResponse<InvoiceResponse> getInvoiceByPurchaseOrderId(@PathVariable("purchaseOrderId") Long purchaseOrderId);

    record InvoiceResponse(
            Long id,
            String invoiceNumber,
            String orderNumber,
            String purchaseOrderStatus,
            Long supplierId,
            String supplierName,
            java.math.BigDecimal amount,
            String dueDate,
            String status,
            String notes,
            String createdAt
    ) {}
}
