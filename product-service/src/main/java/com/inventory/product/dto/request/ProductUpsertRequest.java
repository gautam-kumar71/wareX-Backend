package com.inventory.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductUpsertRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must not exceed 100 characters")
        String sku,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        @DecimalMin(value = "0.0", inclusive = true, message = "Selling price cannot be negative")
        Double price,

        @DecimalMin(value = "0.0", inclusive = true, message = "Cost price cannot be negative")
        Double costPrice,

        @DecimalMin(value = "0.0", inclusive = true, message = "Tax rate cannot be negative")
        Double taxRate,

        @DecimalMin(value = "0.0", inclusive = true, message = "Weight cannot be negative")
        Double weight,

        @DecimalMin(value = "0.0", inclusive = true, message = "Length cannot be negative")
        Double length,

        @DecimalMin(value = "0.0", inclusive = true, message = "Width cannot be negative")
        Double width,

        @DecimalMin(value = "0.0", inclusive = true, message = "Height cannot be negative")
        Double height,

        @Size(max = 10, message = "Weight unit must not exceed 10 characters")
        String weightUnit,

        @Size(max = 10, message = "Dimension unit must not exceed 10 characters")
        String dimensionUnit,

        @Size(max = 10, message = "Legacy unit must not exceed 10 characters")
        String unit,

        Boolean active,

        @Min(value = 0, message = "Reorder level cannot be negative")
        Integer reorderLevel,

        @Min(value = 0, message = "Maximum stock level cannot be negative")
        Integer maxStockLevel,

        @Min(value = 0, message = "Total stock cannot be negative")
        Integer totalStock,

        @Min(value = 0, message = "Allocated stock cannot be negative")
        Integer allocatedStock
) {}
