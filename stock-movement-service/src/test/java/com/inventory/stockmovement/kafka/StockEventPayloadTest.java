package com.inventory.stockmovement.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockEventPayloadTest {

    @Test
    void builderAndAccessors_roundTripValues() {
        Instant occurredAt = Instant.parse("2026-05-02T10:15:30Z");

        StockEventPayload payload = StockEventPayload.builder()
                .eventId("evt-1")
                .movementType("RECEIPT")
                .productId(11L)
                .productName("Widget")
                .warehouseId(4L)
                .warehouseName("Main")
                .quantityDelta(8)
                .newQuantity(42)
                .availableQuantity(40)
                .referenceId("ref-9")
                .referenceType("PURCHASE_ORDER")
                .lowStock(false)
                .overstock(false)
                .occurredAt(occurredAt)
                .build();

        assertThat(payload.getEventId()).isEqualTo("evt-1");
        assertThat(payload.getMovementType()).isEqualTo("RECEIPT");
        assertThat(payload.getProductId()).isEqualTo(11L);
        assertThat(payload.getProductName()).isEqualTo("Widget");
        assertThat(payload.getWarehouseId()).isEqualTo(4L);
        assertThat(payload.getWarehouseName()).isEqualTo("Main");
        assertThat(payload.getQuantityDelta()).isEqualTo(8);
        assertThat(payload.getNewQuantity()).isEqualTo(42);
        assertThat(payload.getAvailableQuantity()).isEqualTo(40);
        assertThat(payload.getReferenceId()).isEqualTo("ref-9");
        assertThat(payload.getReferenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(payload.isLowStock()).isFalse();
        assertThat(payload.isOverstock()).isFalse();
        assertThat(payload.getOccurredAt()).isEqualTo(occurredAt);
    }
}
