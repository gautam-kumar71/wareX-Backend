package com.inventory.purchaseorder.controller;

import com.inventory.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import com.inventory.purchaseorder.dto.request.ReceiveStockRequest;
import com.inventory.purchaseorder.dto.response.ApiResponse;
import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Full lifecycle — create, submit, approve, receive, cancel")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Create a new purchase order (DRAFT)",
            description = "Validates supplier and warehouse via Feign before creating.")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody CreatePurchaseOrderRequest req,
            @RequestHeader("X-User-Id") String userId) {

        PurchaseOrderResponse response = poService.createPurchaseOrder(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Purchase order created successfully"));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all purchase orders (paginated)",
            description = "Filter by status using ?status=DRAFT|SUBMITTED|APPROVED|etc.")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getAll(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<PurchaseOrderResponse> page;
        if (warehouseId != null) {
            page = poService.getByWarehouse(warehouseId, pageable);
        } else if (status != null) {
            page = poService.getByStatus(status, pageable);
        } else {
            page = poService.getAll(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get purchase order by ID")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(poService.getById(id)));
    }

    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get purchase order by order number (e.g. PO-20250901-A3F9C)")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getByOrderNumber(
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(
                ApiResponse.success(poService.getByOrderNumber(orderNumber)));
    }

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all purchase orders for a supplier")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getBySupplier(
            @PathVariable Long supplierId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(poService.getBySupplier(supplierId, pageable)));
    }

    @GetMapping("/internal/suppliers/{supplierId}/deactivation-check")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check whether a supplier can be deactivated safely")
    public ResponseEntity<ApiResponse<PurchaseOrderService.SupplierDeactivationCheckResponse>> getSupplierDeactivationCheck(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(ApiResponse.success(
                poService.getSupplierDeactivationCheck(supplierId)));
    }

    // ── State Transitions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    @Operation(summary = "Submit a DRAFT order for approval",
            description = "Transitions: DRAFT → SUBMITTED")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> submit(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(
                ApiResponse.success(poService.submitOrder(id, userId),
                        "Purchase order submitted for approval"));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'PURCHASE_OFFICER')")
    @Operation(summary = "Approve a submitted order",
            description = "Transitions: SUBMITTED → APPROVED")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(
                ApiResponse.success(poService.approveOrder(id, userId),
                        "Purchase order approved"));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Record receipt of goods for one product line",
            description = "Transitions: APPROVED → PARTIALLY_RECEIVED → RECEIVED. "
                    + "Calls Warehouse Service to physically add stock. "
                    + "Can be called multiple times for partial receipts.")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receiveStock(
            @PathVariable Long id,
            @Valid @RequestBody ReceiveStockRequest req,
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(
                ApiResponse.success(poService.receiveStock(id, req, userId),
                        "Stock received successfully"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'PURCHASE_OFFICER')")
    @Operation(summary = "Cancel a purchase order",
            description = "Transitions from DRAFT/SUBMITTED/APPROVED → CANCELLED. "
                    + "Terminal states (RECEIVED, CANCELLED) cannot be cancelled.")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(
            @PathVariable Long id,
            @RequestParam @NotBlank(message = "Cancellation reason is required") String reason,
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(
                ApiResponse.success(poService.cancelOrder(id, reason, userId),
                        "Purchase order cancelled"));
    }
}
