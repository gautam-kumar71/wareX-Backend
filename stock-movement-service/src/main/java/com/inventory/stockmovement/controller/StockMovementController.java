package com.inventory.stockmovement.controller;

import com.inventory.stockmovement.dto.response.ApiResponse;
import com.inventory.stockmovement.dto.response.StockMovementResponse;
import com.inventory.stockmovement.enums.MovementType;
import com.inventory.stockmovement.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-movements")
@RequiredArgsConstructor
@Tag(name = "Stock Movements", description = "Immutable audit trail — read-only queries")
@SecurityRequirement(name = "bearerAuth")
public class StockMovementController {

    private final StockMovementService movementService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all stock movements (paginated, newest first)")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getAll(
            @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(movementService.getAll(pageable)));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all movements for a product across all warehouses")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(movementService.getByProduct(productId, pageable)));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all movements in a warehouse")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByWarehouse(
            @PathVariable Long warehouseId,
            @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(movementService.getByWarehouse(warehouseId, pageable)));
    }

    @GetMapping("/product/{productId}/warehouse/{warehouseId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get movements for a specific product in a specific warehouse")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByProductAndWarehouse(
            @PathVariable Long productId,
            @PathVariable Long warehouseId,
            @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                movementService.getByProductAndWarehouse(productId, warehouseId, pageable)));
    }

    @GetMapping("/type/{movementType}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all movements of a specific type (RECEIPT, SALE, TRANSFER_IN, etc.)")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByType(
            @PathVariable MovementType movementType,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(movementService.getByType(movementType, pageable)));
    }

    @GetMapping("/date-range")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get movements within a date range",
            description = "Both from and to are ISO-8601 UTC timestamps, e.g. 2025-09-01T00:00:00Z")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(movementService.getByDateRange(from, to, pageable)));
    }

    @GetMapping("/product/{productId}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get complete movement history for a product in a date range (for charts)")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getProductHistory(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(
                movementService.getByProductAndDateRange(productId, from, to)));
    }

    @GetMapping("/reference")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get movements by reference (e.g. all movements from a specific PO or order)",
            description = "referenceType: PURCHASE_ORDER, SALE_ORDER, TRANSFER, MANUAL, etc.")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getByReference(
            @RequestParam String referenceId,
            @RequestParam String referenceType,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                movementService.getByReference(referenceId, referenceType, pageable)));
    }
}