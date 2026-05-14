package com.inventory.warehouse.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferStockRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Source warehouse ID is required")
        Long sourceWarehouseId,

        @NotNull(message = "Destination warehouse ID is required")
        Long destinationWarehouseId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Transfer quantity must be greater than zero")
        Integer quantity,

        // Optional reference — e.g. internal transfer request ID
        String referenceId
) {}