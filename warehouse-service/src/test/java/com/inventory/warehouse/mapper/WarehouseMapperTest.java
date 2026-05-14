package com.inventory.warehouse.mapper;

import com.inventory.warehouse.dto.response.WarehouseResponse;
import com.inventory.warehouse.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseMapperTest {

    private final WarehouseMapper mapper = new WarehouseMapper();

    @Test
    void toResponse_mapsWarehouseFields() {
        Warehouse warehouse = Warehouse.builder()
                .id(5L)
                .name("Central")
                .location("Plot 5")
                .city("Pune")
                .country("India")
                .totalStorageCapacity(500)
                .currentCapacityUtilization(300)
                .managerName("Alex")
                .contactPhone("9999")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();

        WarehouseResponse response = mapper.toResponse(warehouse);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Central");
        assertThat(response.location()).isEqualTo("Plot 5");
        assertThat(response.city()).isEqualTo("Pune");
        assertThat(response.country()).isEqualTo("India");
        assertThat(response.managerName()).isEqualTo("Alex");
        assertThat(response.contactPhone()).isEqualTo("9999");
        assertThat(response.active()).isTrue();
    }
}
