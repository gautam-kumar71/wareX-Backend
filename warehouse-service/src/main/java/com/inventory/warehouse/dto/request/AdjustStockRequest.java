package com.inventory.warehouse.dto.request;

import com.inventory.warehouse.enums.AdjustmentReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        // Positive for adding stock, negative for removing.
        // Cannot be zero — a zero adjustment is meaningless.
        @NotNull(message = "Quantity delta is required")
        Integer quantityDelta,

        @NotNull(message = "Adjustment reason is required")
        AdjustmentReason reason,

        // Optional free-text note for audit trail
        String notes,

        @Min(value = 0, message = "Reorder point cannot be negative")
        Integer reorderPoint,

        Integer maxCapacity
) {}