package com.inventory.purchaseorder.feign;

import com.inventory.purchaseorder.feign.fallback.SupplierClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "supplier-service",
        fallback = SupplierClientFallback.class
)
public interface SupplierClient {

    @GetMapping("/api/v1/suppliers/{id}")
    ApiResponse<SupplierResponse> getSupplierById(@PathVariable("id") Long id);

    record ApiResponse<T>(T data) {}
    record SupplierResponse(Long id, String name, String contactEmail, boolean active) {}
}