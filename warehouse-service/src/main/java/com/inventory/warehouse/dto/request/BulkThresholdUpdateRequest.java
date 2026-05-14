package com.inventory.warehouse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkThresholdUpdateRequest(

        @NotEmpty(message = "At least one product must be selected")
        List<@NotNull(message = "Product ID is required") Long> productIds,

        @Min(value = 0, message = "Reorder point cannot be negative")
        Integer reorderPoint,

        @Min(value = 0, message = "Max capacity cannot be negative")
        Integer maxCapacity
) {}
