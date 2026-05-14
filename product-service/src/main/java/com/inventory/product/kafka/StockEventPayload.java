package com.inventory.product.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class StockEventPayload {
    private String eventId;
    private String movementType; // e.g., RECEIPT, SALE, ADJUSTMENT
    private Long productId;
    private Long warehouseId;
    private int quantityDelta;
    private int newQuantity; // Quantity in that specific warehouse
    private String referenceId;
    private String referenceType;
    private Instant occurredAt;
}
