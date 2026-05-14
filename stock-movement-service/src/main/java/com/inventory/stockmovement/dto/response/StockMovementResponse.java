package com.inventory.stockmovement.dto.response;

import com.inventory.stockmovement.enums.MovementType;

import java.time.Instant;

public record StockMovementResponse(
        Long         id,
        String       eventId,
        Long         productId,
        String       productName,
        Long         warehouseId,
        String       warehouseName,
        MovementType movementType,
        int          quantityDelta,
        int          quantityAfter,
        String       referenceId,
        String       referenceType,
        String       transactionId,
        String       notes,
        Instant      occurredAt,
        Instant      recordedAt
) {}
