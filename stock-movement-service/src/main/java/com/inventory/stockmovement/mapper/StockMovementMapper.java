package com.inventory.stockmovement.mapper;

import com.inventory.stockmovement.dto.response.StockMovementResponse;
import com.inventory.stockmovement.entity.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMovementMapper {

    public StockMovementResponse toResponse(StockMovement movement) {
        if (movement == null) {
            return null;
        }

        return new StockMovementResponse(
                movement.getId(),
                movement.getEventId(),
                movement.getProductId(),
                movement.getProductName(),
                movement.getWarehouseId(),
                movement.getWarehouseName(),
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getQuantityAfter(),
                movement.getReferenceId(),
                movement.getReferenceType(),
                null,
                movement.getNotes(),
                movement.getOccurredAt(),
                movement.getRecordedAt()
        );
    }
}
