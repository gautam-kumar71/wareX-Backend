package com.inventory.supplier.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "purchase-order-service")
public interface PurchaseOrderClient {

    @GetMapping("/api/v1/purchase-orders/internal/suppliers/{supplierId}/deactivation-check")
    ApiResponse<SupplierDeactivationCheckResponse> getSupplierDeactivationCheck(
            @PathVariable("supplierId") Long supplierId);

    record ApiResponse<T>(T data) {}

    record SupplierDeactivationCheckResponse(
            boolean canDeactivate,
            long blockingOrderCount,
            java.util.List<String> blockingStatuses,
            java.util.List<String> blockingOrderNumbers,
            long blockingInvoiceCount,
            java.util.List<String> blockingInvoiceStatuses,
            java.util.List<String> blockingInvoiceNumbers,
            String message
    ) {}
}
