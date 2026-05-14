package com.inventory.purchaseorder.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotBlank(message = "Product name is required")
        @Size(max = 255)
        String productName,

        @NotBlank(message = "Product SKU is required")
        @Size(max = 100)
        String productSku,

        @NotNull(message = "Ordered quantity is required")
        @Positive(message = "Ordered quantity must be greater than zero")
        Integer orderedQty,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0001", message = "Unit price must be greater than zero")
        BigDecimal unitPrice
) {}