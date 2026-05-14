package com.inventory.product.kafka;

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
                .warehouseId(4L)
                .quantityDelta(8)
                .newQuantity(42)
                .referenceId("ref-9")
                .referenceType("PURCHASE_ORDER")
                .occurredAt(occurredAt)
                .build();

        assertThat(payload.getEventId()).isEqualTo("evt-1");
        assertThat(payload.getMovementType()).isEqualTo("RECEIPT");
        assertThat(payload.getProductId()).isEqualTo(11L);
        assertThat(payload.getWarehouseId()).isEqualTo(4L);
        assertThat(payload.getQuantityDelta()).isEqualTo(8);
        assertThat(payload.getNewQuantity()).isEqualTo(42);
        assertThat(payload.getReferenceId()).isEqualTo("ref-9");
        assertThat(payload.getReferenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(payload.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void noArgsConstructorAndSetters_allowMutation() {
        Instant occurredAt = Instant.parse("2026-05-03T00:00:00Z");
        StockEventPayload payload = new StockEventPayload();

        payload.setEventId("evt-2");
        payload.setMovementType("SALE");
        payload.setProductId(15L);
        payload.setWarehouseId(7L);
        payload.setQuantityDelta(-2);
        payload.setNewQuantity(19);
        payload.setReferenceId("ref-11");
        payload.setReferenceType("ORDER");
        payload.setOccurredAt(occurredAt);

        assertThat(payload.getEventId()).isEqualTo("evt-2");
        assertThat(payload.getMovementType()).isEqualTo("SALE");
        assertThat(payload.getProductId()).isEqualTo(15L);
        assertThat(payload.getWarehouseId()).isEqualTo(7L);
        assertThat(payload.getQuantityDelta()).isEqualTo(-2);
        assertThat(payload.getNewQuantity()).isEqualTo(19);
        assertThat(payload.getReferenceId()).isEqualTo("ref-11");
        assertThat(payload.getReferenceType()).isEqualTo("ORDER");
        assertThat(payload.getOccurredAt()).isEqualTo(occurredAt);
    }
}
