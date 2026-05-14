package com.inventory.purchaseorder.dto.response;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNumber,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        PurchaseOrderStatus status,
        BigDecimal totalAmount,
        String notes,
        String createdBy,
        String approvedBy,
        String cancelledBy,
        String cancelReason,
        LocalDate expectedDate,
        Instant receivedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PurchaseOrderLineResponse> lines
) {}