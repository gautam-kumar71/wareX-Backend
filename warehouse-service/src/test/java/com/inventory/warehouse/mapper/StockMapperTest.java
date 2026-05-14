package com.inventory.warehouse.mapper;

import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.entity.StockLevel;
import com.inventory.warehouse.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockMapperTest {

    private final StockMapper mapper = new StockMapper();

    @Test
    void toResponse_mapsNestedWarehouseAndComputedFields() {
        Warehouse warehouse = Warehouse.builder().id(3L).name("Main").build();
        StockLevel level = StockLevel.builder()
                .id(10L)
                .warehouse(warehouse)
                .productId(101L)
                .productName("Widget")
                .sku("SKU-101")
                .quantity(20)
                .reservedQty(5)
                .reorderPoint(25)
                .maxCapacity(20)
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        StockLevelResponse response = mapper.toResponse(level);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.warehouseId()).isEqualTo(3L);
        assertThat(response.warehouseName()).isEqualTo("Main");
        assertThat(response.productId()).isEqualTo(101L);
        assertThat(response.availableQty()).isEqualTo(15);
        assertThat(response.lowStock()).isFalse();
        assertThat(response.overstock()).isTrue();
    }
}
