package com.inventory.payment.feign;

import com.inventory.payment.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "purchase-order-service", configuration = FeignConfig.class)
public interface PurchaseOrderClient {

    @GetMapping("/api/v1/invoices/number/{invoiceNumber}")
    ApiResponse<InvoiceResponse> getInvoiceByNumber(@PathVariable("invoiceNumber") String invoiceNumber);

    @PostMapping("/api/v1/invoices/number/{invoiceNumber}/paid")
    ApiResponse<InvoiceResponse> markInvoicePaid(@PathVariable("invoiceNumber") String invoiceNumber,
                                                 @RequestParam("transactionId") String transactionId);

    @PostMapping("/api/v1/invoices/number/{invoiceNumber}/payment-cancelled")
    ApiResponse<Void> cancelOrderForPayment(@PathVariable("invoiceNumber") String invoiceNumber,
                                            @RequestParam("transactionId") String transactionId,
                                            @RequestParam("reason") String reason);

    record ApiResponse<T>(
            String timestamp,
            int status,
            String message,
            T data
    ) {}

    record InvoiceResponse(
            Long id,
            String invoiceNumber,
            String orderNumber,
            String purchaseOrderStatus,
            Long supplierId,
            String supplierName,
            BigDecimal amount,
            String dueDate,
            String status,
            String notes,
            String createdAt
    ) {}
}
