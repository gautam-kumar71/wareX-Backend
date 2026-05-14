package com.inventory.warehouse.mapper;

import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.entity.StockLevel;
import org.springframework.stereotype.Component;

@Component
public class StockMapper {

    public StockLevelResponse toResponse(StockLevel stockLevel) {
        if (stockLevel == null) {
            return null;
        }

        Long warehouseId = stockLevel.getWarehouse() != null ? stockLevel.getWarehouse().getId() : null;
        String warehouseName = stockLevel.getWarehouse() != null ? stockLevel.getWarehouse().getName() : null;

        return new StockLevelResponse(
                stockLevel.getId(),
                warehouseId,
                warehouseName,
                stockLevel.getProductId(),
                stockLevel.getProductName(),
                stockLevel.getSku(),
                stockLevel.getQuantity(),
                stockLevel.getReservedQty(),
                stockLevel.getAvailableQuantity(),
                stockLevel.getReorderPoint(),
                stockLevel.getMaxCapacity(),
                stockLevel.isLowStock(),
                stockLevel.isOverstock(),
                stockLevel.getUpdatedAt()
        );
    }
}
