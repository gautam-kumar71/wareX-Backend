package com.inventory.stockmovement.entity;

import com.inventory.stockmovement.enums.MovementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementTest {

    @Test
    void builderAndSetters_roundTripFields() {
        Instant occurredAt = Instant.parse("2026-05-02T10:15:30Z");
        Instant recordedAt = Instant.parse("2026-05-02T10:16:30Z");

        StockMovement movement = StockMovement.builder()
                .id(1L)
                .eventId("evt-1")
                .productId(11L)
                .productName("Widget")
                .warehouseId(4L)
                .warehouseName("Main")
                .movementType(MovementType.RECEIPT)
                .quantityDelta(8)
                .quantityAfter(42)
                .referenceId("ref-9")
                .referenceType("PURCHASE_ORDER")
                .notes("restocked")
                .occurredAt(occurredAt)
                .recordedAt(recordedAt)
                .build();

        assertThat(movement.getId()).isEqualTo(1L);
        assertThat(movement.getEventId()).isEqualTo("evt-1");
        assertThat(movement.getProductId()).isEqualTo(11L);
        assertThat(movement.getProductName()).isEqualTo("Widget");
        assertThat(movement.getWarehouseId()).isEqualTo(4L);
        assertThat(movement.getWarehouseName()).isEqualTo("Main");
        assertThat(movement.getMovementType()).isEqualTo(MovementType.RECEIPT);
        assertThat(movement.getQuantityDelta()).isEqualTo(8);
        assertThat(movement.getQuantityAfter()).isEqualTo(42);
        assertThat(movement.getReferenceId()).isEqualTo("ref-9");
        assertThat(movement.getReferenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(movement.getNotes()).isEqualTo("restocked");
        assertThat(movement.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(movement.getRecordedAt()).isEqualTo(recordedAt);
    }
}
