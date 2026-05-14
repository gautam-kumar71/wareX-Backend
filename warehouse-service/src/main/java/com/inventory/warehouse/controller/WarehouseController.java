package com.inventory.warehouse.controller;

import com.inventory.warehouse.dto.request.CreateWarehouseRequest;
import com.inventory.warehouse.dto.request.UpdateWarehouseRequest;
import com.inventory.warehouse.dto.response.ApiResponse;
import com.inventory.warehouse.dto.response.WarehouseResponse;
import com.inventory.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Warehouse CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Create a new warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(
            @Valid @RequestBody CreateWarehouseRequest req) {
        WarehouseResponse response = warehouseService.createWarehouse(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Warehouse created successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all warehouses",
            description = "Pass ?activeOnly=true to return only active warehouses (default: all)")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAll(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get warehouse by ID")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Update warehouse details")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest req) {
        WarehouseResponse response = warehouseService.updateWarehouse(id, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Warehouse updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a warehouse (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        warehouseService.deactivateWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Warehouse deactivated successfully"));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate an inactive warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> reactivate(@PathVariable Long id) {
        WarehouseResponse response = warehouseService.reactivateWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Warehouse reactivated successfully"));
    }
}
