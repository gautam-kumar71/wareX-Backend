package com.inventory.warehouse.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseTest {

    @Test
    void builderDefaultsAndLifecycleMethodsWork() {
        Warehouse warehouse = Warehouse.builder()
                .name("Main")
                .location("Plot")
                .city("Pune")
                .build();

        assertThat(warehouse.getCountry()).isEqualTo("India");
        assertThat(warehouse.isActive()).isTrue();
        assertThat(warehouse.getCurrentCapacityUtilization()).isZero();
        assertThat(warehouse.getStockLevels()).isEmpty();

        warehouse.prePersist();
        assertThat(warehouse.getCreatedAt()).isNotNull();
        assertThat(warehouse.getUpdatedAt()).isNotNull();

        warehouse.preUpdate();
        assertThat(warehouse.getUpdatedAt()).isNotNull();
    }
}
