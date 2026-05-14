package com.inventory.purchaseorder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseOrderRequest(

        @NotNull(message = "Supplier ID is required")
        Long supplierId,

        @NotNull(message = "Warehouse ID is required")
        Long warehouseId,

        @NotNull(message = "At least one order line is required")
        @Size(min = 1, message = "At least one order line is required")
        @Valid
        List<PurchaseOrderLineRequest> lines,

        String notes,

        LocalDate expectedDate
) {}