package com.inventory.warehouse.dto.response;

import java.time.Instant;

public record StockLevelResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productName,
        String sku,
        int quantity,
        int reservedQty,
        int availableQty,
        int reorderPoint,
        Integer maxCapacity,
        boolean lowStock,
        boolean overstock,
        Instant updatedAt
) {}
