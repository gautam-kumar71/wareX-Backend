package com.inventory.warehouse.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockLevelTest {

    @Test
    void helpersAndLifecycleMethodsWork() {
        StockLevel level = StockLevel.builder()
                .quantity(12)
                .reservedQty(4)
                .reorderPoint(15)
                .maxCapacity(12)
                .build();

        level.prePersist();
        level.preUpdate();

        assertThat(level.getCreatedAt()).isNotNull();
        assertThat(level.getUpdatedAt()).isNotNull();
        assertThat(level.getAvailableQuantity()).isEqualTo(8);
        assertThat(level.isLowStock()).isFalse();
        assertThat(level.isOverstock()).isTrue();
    }

    @Test
    void availableQuantityNeverDropsBelowZero() {
        StockLevel level = StockLevel.builder()
                .quantity(2)
                .reservedQty(5)
                .reorderPoint(0)
                .build();

        assertThat(level.getAvailableQuantity()).isZero();
        assertThat(level.isLowStock()).isFalse();
        assertThat(level.isOverstock()).isFalse();
    }

    @Test
    void lowStockStaysTrueWhenBelowReorderPointAndNotAtMaxCapacity() {
        StockLevel level = StockLevel.builder()
                .quantity(8)
                .reservedQty(0)
                .reorderPoint(10)
                .maxCapacity(20)
                .build();

        assertThat(level.isLowStock()).isTrue();
        assertThat(level.isOverstock()).isFalse();
    }
}
