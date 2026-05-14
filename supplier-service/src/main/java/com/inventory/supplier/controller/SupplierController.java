package com.inventory.supplier.controller;

import com.inventory.supplier.dto.request.CreateSupplierRequest;
import com.inventory.supplier.dto.request.UpdateSupplierRequest;
import com.inventory.supplier.dto.response.ApiResponse;
import com.inventory.supplier.dto.response.SupplierDeactivationCheckResponse;
import com.inventory.supplier.dto.response.SupplierResponse;
import com.inventory.supplier.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Supplier CRUD and search")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASE_OFFICER')")
    @Operation(summary = "Create a new supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody CreateSupplierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierService.create(req), "Supplier created successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all suppliers (paginated)",
            description = "Use ?active=true or ?active=false to filter by supplier status. Add ?activeOnly=true for legacy active-only filtering.")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> getAll(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Boolean activeFilter = active != null ? active : (activeOnly ? Boolean.TRUE : null);
        return ResponseEntity.ok(ApiResponse.success(supplierService.getAll(activeFilter, pageable)));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all active suppliers (no pagination — for dropdowns)")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getAllActive() {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getAllActive()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get supplier by ID")
    public ResponseEntity<ApiResponse<SupplierResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getById(id)));
    }

    @GetMapping("/{id}/deactivation-check")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Check whether a supplier can be deactivated safely")
    public ResponseEntity<ApiResponse<SupplierDeactivationCheckResponse>> getDeactivationCheck(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getDeactivationCheck(id)));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search suppliers by name, email, contact, territory, category, or GSTIN")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.search(q, active, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASE_OFFICER')")
    @Operation(summary = "Update supplier details")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(supplierService.update(id, req), "Supplier updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a supplier (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        supplierService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Supplier deactivated successfully"));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate a suspended supplier")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long id) {
        supplierService.reactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Supplier reactivated successfully"));
    }
}
