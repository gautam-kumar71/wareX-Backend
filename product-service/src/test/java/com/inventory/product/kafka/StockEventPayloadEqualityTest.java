package com.inventory.product.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockEventPayloadEqualityTest {

    @Test
    void equalsHashCodeAndToString_coverGeneratedBranches() {
        Instant occurredAt = Instant.parse("2026-05-03T10:15:30Z");

        StockEventPayload left = StockEventPayload.builder()
                .eventId("evt-1")
                .movementType("RECEIPT")
                .productId(1L)
                .warehouseId(2L)
                .quantityDelta(5)
                .newQuantity(20)
                .referenceId("PO-1")
                .referenceType("PURCHASE_ORDER")
                .occurredAt(occurredAt)
                .build();

        StockEventPayload same = StockEventPayload.builder()
                .eventId("evt-1")
                .movementType("RECEIPT")
                .productId(1L)
                .warehouseId(2L)
                .quantityDelta(5)
                .newQuantity(20)
                .referenceId("PO-1")
                .referenceType("PURCHASE_ORDER")
                .occurredAt(occurredAt)
                .build();

        StockEventPayload different = StockEventPayload.builder()
                .eventId("evt-2")
                .movementType("SALE")
                .productId(3L)
                .warehouseId(4L)
                .quantityDelta(-1)
                .newQuantity(19)
                .referenceId("SO-1")
                .referenceType("SALE_ORDER")
                .occurredAt(occurredAt.plusSeconds(60))
                .build();

        assertThat(left).isEqualTo(left);
        assertThat(left).isEqualTo(same);
        assertThat(left.hashCode()).isEqualTo(same.hashCode());
        assertThat(left).isNotEqualTo(different);
        assertThat(left).isNotEqualTo(null);
        assertThat(left).isNotEqualTo("payload");
        assertThat(left.toString()).contains("eventId=evt-1", "movementType=RECEIPT");
    }
}
