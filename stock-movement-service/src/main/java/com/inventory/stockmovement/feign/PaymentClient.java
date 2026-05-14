package com.inventory.stockmovement.feign;

import com.inventory.stockmovement.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "payment-service", url = "${app.services.payment-service.url}")
public interface PaymentClient {

    @GetMapping("/api/v1/payments/invoice/{invoiceNumber}/latest")
    ApiResponse<PaymentResponse> getLatestPaymentByInvoiceNumber(@PathVariable("invoiceNumber") String invoiceNumber);

    record PaymentResponse(
            Long id,
            String transactionId,
            String invoiceNumber,
            java.math.BigDecimal amount,
            String paymentMethod,
            String status,
            String referenceNotes,
            String processedBy,
            java.time.Instant createdAt
    ) {}
}
