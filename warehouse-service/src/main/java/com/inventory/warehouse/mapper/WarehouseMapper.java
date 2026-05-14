package com.inventory.warehouse.mapper;

import com.inventory.warehouse.dto.response.WarehouseResponse;
import com.inventory.warehouse.entity.Warehouse;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse warehouse) {
        if (warehouse == null) {
            return null;
        }

        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.getCity(),
                warehouse.getCountry(),
                warehouse.getTotalStorageCapacity(),
                warehouse.getCurrentCapacityUtilization(),
                null,
                null,
                null,
                warehouse.getManagerName(),
                warehouse.getContactPhone(),
                null,
                null,
                null,
                null,
                warehouse.isActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }
}
