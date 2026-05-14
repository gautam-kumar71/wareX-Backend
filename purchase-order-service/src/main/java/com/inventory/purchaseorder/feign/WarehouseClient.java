package com.inventory.purchaseorder.feign;

import com.inventory.purchaseorder.feign.fallback.WarehouseClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for Warehouse Service.
 *
 * Used to:
 *   1. Validate that the warehouse exists and is active before creating a PO
 *   2. Trigger stock receipt when goods arrive (POST /receive)
 *
 * Resilience4j circuit breaker wraps all calls.
 * If Warehouse Service is down, fallback returns a safe degraded response.
 *
 * Note: The gateway JWT filter injects X-User-Id and X-User-Role.
 * For internal Feign calls between services (bypassing gateway), we pass
 * the headers via RequestInterceptor in FeignConfig.
 */
@FeignClient(
        name = "warehouse-service",
        fallback = WarehouseClientFallback.class
)
public interface WarehouseClient {
 
    @GetMapping("/api/v1/warehouses/{id}")
    ApiResponse<WarehouseResponse> getWarehouseById(@PathVariable("id") Long id);

    @PostMapping("/api/v1/stock/warehouses/{warehouseId}/products/{productId}/receive")
    void receiveStock(
            @PathVariable("warehouseId") Long warehouseId,
            @PathVariable("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam("purchaseOrderId") String purchaseOrderId
    );

    @PostMapping("/api/v1/stock/warehouses/{warehouseId}/products/{productId}/receipt-reversal")
    void reverseReceivedStock(
            @PathVariable("warehouseId") Long warehouseId,
            @PathVariable("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam("purchaseOrderId") String purchaseOrderId
    );

    // Lightweight DTO for warehouse validation response
    record ApiResponse<T>(T data) {}
    record WarehouseResponse(
            Long id,
            String name,
            String location,
            String city,
            String country,
            Integer totalStorageCapacity,
            Integer currentCapacityUtilization,
            Integer capacityPercent,
            Integer lowStockItemCount,
            Integer overstockItemCount,
            String managerName,
            String contactPhone,
            Long suggestedTransferWarehouseId,
            String suggestedTransferWarehouseName,
            Integer suggestedTransferFreeCapacity,
            String capacityAdvisory,
            boolean active
    ) {}
}
