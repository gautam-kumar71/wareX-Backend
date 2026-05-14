package com.inventory.warehouse.event;

import com.inventory.warehouse.enums.MovementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockEventTest {

    @Test
    void builderAndAccessorsPreserveValues() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        StockEvent event = StockEvent.builder()
                .eventId("evt-1")
                .movementType(MovementType.ADJUSTMENT_ADD)
                .productId(10L)
                .productName("Widget")
                .warehouseId(5L)
                .warehouseName("Main")
                .quantityDelta(7)
                .newQuantity(70)
                .availableQuantity(60)
                .referenceId("REF-1")
                .referenceType("MANUAL")
                .lowStock(false)
                .overstock(true)
                .occurredAt(now)
                .build();

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getMovementType()).isEqualTo(MovementType.ADJUSTMENT_ADD);
        assertThat(event.getProductId()).isEqualTo(10L);
        assertThat(event.getWarehouseName()).isEqualTo("Main");
        assertThat(event.isOverstock()).isTrue();
        assertThat(event.getOccurredAt()).isEqualTo(now);
    }
}
