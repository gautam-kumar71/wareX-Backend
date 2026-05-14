package com.inventory.warehouse.controller;

import com.inventory.warehouse.dto.request.AdjustStockRequest;
import com.inventory.warehouse.dto.request.BulkThresholdUpdateRequest;
import com.inventory.warehouse.dto.request.TransferStockRequest;
import com.inventory.warehouse.dto.response.ApiResponse;
import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Stock level queries, adjustments, and transfers")
@SecurityRequirement(name = "bearerAuth")
public class StockController {

    private final StockService stockService;

    // ── Initialize ────────────────────────────────────────────────────────────

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/initialize")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Initialize stock for a product in a warehouse",
            description = "Creates the stock record for a product/warehouse pair. "
                    + "Call this once when onboarding a new product.")
    public ResponseEntity<ApiResponse<StockLevelResponse>> initialize(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") @Min(0) int initialQty,
            @RequestParam(defaultValue = "0") @Min(0) int reorderPoint,
            @RequestParam(required = false) Integer maxCapacity) {

        StockLevelResponse response = stockService.initializeStock(
                warehouseId, productId, initialQty, reorderPoint, maxCapacity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Stock level initialized"));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @GetMapping("/warehouses/{warehouseId}/products/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get stock level for a specific product in a warehouse")
    public ResponseEntity<ApiResponse<StockLevelResponse>> getStockLevel(
            @PathVariable Long warehouseId,
            @PathVariable Long productId) {
        return ResponseEntity.ok(
                ApiResponse.success(stockService.getStockLevel(warehouseId, productId)));
    }

    @GetMapping("/warehouses/{warehouseId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all stock levels in a warehouse")
    public ResponseEntity<ApiResponse<List<StockLevelResponse>>> getByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "false") boolean overstockOnly,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                ApiResponse.success(stockService.getStockByWarehouse(
                        warehouseId, q, lowStockOnly, overstockOnly, sortBy, sortDir)));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get stock levels for a product across all warehouses")
    public ResponseEntity<ApiResponse<List<StockLevelResponse>>> getByProduct(
            @PathVariable Long productId) {
        return ResponseEntity.ok(
                ApiResponse.success(stockService.getStockByProduct(productId)));
    }

    @GetMapping("/products/{productId}/total")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get total on-hand quantity for a product across all warehouses")
    public ResponseEntity<ApiResponse<Integer>> getTotalStock(@PathVariable Long productId) {
        return ResponseEntity.ok(
                ApiResponse.success(stockService.getTotalStockForProduct(productId)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all stock levels currently below their reorder point")
    public ResponseEntity<ApiResponse<List<StockLevelResponse>>> getLowStock(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "availableQty") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(
                ApiResponse.success(stockService.getLowStockItems(warehouseId, q, sortBy, sortDir)));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @PatchMapping("/warehouses/{warehouseId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF')")
    @Operation(summary = "Manually adjust stock level",
            description = "Use positive quantityDelta to add stock, negative to remove. "
                    + "Negative stock is rejected with 409. "
                    + "Concurrent updates use optimistic locking — 409 means retry.")
    public ResponseEntity<ApiResponse<StockLevelResponse>> adjustStock(
            @PathVariable Long warehouseId,
            @Valid @RequestBody AdjustStockRequest req) {
        StockLevelResponse response = stockService.adjustStock(warehouseId, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Stock adjusted successfully"));
    }

    @PatchMapping("/warehouses/{warehouseId}/thresholds")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Bulk update reorder points and max capacities",
            description = "Updates threshold settings for multiple stock rows in a warehouse without changing quantities.")
    public ResponseEntity<ApiResponse<List<StockLevelResponse>>> bulkUpdateThresholds(
            @PathVariable Long warehouseId,
            @Valid @RequestBody BulkThresholdUpdateRequest req) {
        List<StockLevelResponse> response = stockService.bulkUpdateThresholds(warehouseId, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Thresholds updated successfully"));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Transfer stock between warehouses",
            description = "Atomically debits source and credits destination. "
                    + "Both warehouses must be active and source must have sufficient available stock. "
                    + "Deadlock-safe: locks acquired in deterministic order.")
    public ResponseEntity<ApiResponse<Void>> transferStock(
            @Valid @RequestBody TransferStockRequest req) {
        stockService.transferStock(req);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Stock transferred successfully"));
    }

    // ── Reservation endpoints (called by Purchase Order / Sale Order services) ─

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/reserve")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF')")
    @Operation(summary = "Reserve stock for an order (soft lock)")
    public ResponseEntity<ApiResponse<StockLevelResponse>> reserve(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestParam @NotNull @Min(1) Integer quantity,
            @RequestParam @NotNull String orderId) {
        StockLevelResponse response =
                stockService.reserveStock(warehouseId, productId, quantity, orderId);
        return ResponseEntity.ok(ApiResponse.success(response, "Stock reserved"));
    }

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/release")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF')")
    @Operation(summary = "Release a stock reservation (order cancelled)")
    public ResponseEntity<ApiResponse<StockLevelResponse>> release(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestParam @NotNull @Min(1) Integer quantity,
            @RequestParam @NotNull String orderId) {
        StockLevelResponse response =
                stockService.releaseReservation(warehouseId, productId, quantity, orderId);
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation released"));
    }

    // ── Internal endpoint (called by Purchase Order Service via Feign) ─────────

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASE_OFFICER','WAREHOUSE_STAFF')")
    @Operation(summary = "Receive stock from a purchase order",
            description = "Called internally by Purchase Order Service when goods are received.")
    public ResponseEntity<ApiResponse<StockLevelResponse>> receiveStock(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestParam @NotNull @Min(1) Integer quantity,
            @RequestParam @NotNull String purchaseOrderId) {
        StockLevelResponse response =
                stockService.receiveStock(warehouseId, productId, quantity, purchaseOrderId);
        return ResponseEntity.ok(ApiResponse.success(response, "Stock received"));
    }

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/receipt-reversal")
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASE_OFFICER','WAREHOUSE_STAFF')")
    @Operation(summary = "Reverse received stock after payment cancellation",
            description = "Called internally when a paid purchase order is cancelled and received stock must be removed.")
    public ResponseEntity<ApiResponse<StockLevelResponse>> reverseReceivedStock(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestParam @NotNull @Min(1) Integer quantity,
            @RequestParam @NotNull String purchaseOrderId) {
        StockLevelResponse response =
                stockService.reverseReceivedStock(warehouseId, productId, quantity, purchaseOrderId);
        return ResponseEntity.ok(ApiResponse.success(response, "Received stock reversed"));
    }
}
