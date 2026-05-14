package com.inventory.purchaseorder.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Represents receipt of a single line item — called once per product being received
public record ReceiveStockRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Received quantity is required")
        @Positive(message = "Received quantity must be greater than zero")
        Integer receivedQty
) {}