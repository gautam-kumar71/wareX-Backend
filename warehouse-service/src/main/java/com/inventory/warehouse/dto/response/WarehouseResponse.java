package com.inventory.warehouse.dto.response;

import java.time.Instant;

public record WarehouseResponse(
        Long id,
        String name,
        String location,
        String city,
        String country,
        Integer totalStorageCapacity,
        Integer currentCapacityUtilization,
        Integer capacityPercent,
        Integer lowStockItemCount,
        Integer overstockItemCount,
        String managerName,
        String contactPhone,
        Long suggestedTransferWarehouseId,
        String suggestedTransferWarehouseName,
        Integer suggestedTransferFreeCapacity,
        String capacityAdvisory,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
