package com.inventory.stockmovement.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;

/**
 * Kafka message payload consumed from the "stock-events" topic.
 * Published by Warehouse Service whenever stock changes.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) — tolerates extra fields
 * if Warehouse Service adds new fields in future without breaking this consumer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("java:S1068")
public class StockEventPayload {

    private String  eventId;          // UUID — our idempotency key
    private String  movementType;     // matches MovementType enum names
    private Long    productId;
    private String  productName;
    private Long    warehouseId;
    private String  warehouseName;
    private int     quantityDelta;
    private int     newQuantity;
    private int     availableQuantity;
    private String  referenceId;
    private String  referenceType;
    private boolean lowStock;
    private boolean overstock;
    private Instant occurredAt;
}
