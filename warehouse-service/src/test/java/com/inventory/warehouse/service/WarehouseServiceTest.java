package com.inventory.warehouse.service;

import com.inventory.warehouse.dto.request.CreateWarehouseRequest;
import com.inventory.warehouse.dto.request.UpdateWarehouseRequest;
import com.inventory.warehouse.dto.response.WarehouseResponse;
import com.inventory.warehouse.entity.StockLevel;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.exception.WarehouseNotFoundException;
import com.inventory.warehouse.mapper.WarehouseMapper;
import com.inventory.warehouse.repository.StockLevelRepository;
import com.inventory.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepo;

    @Mock
    private StockLevelRepository stockRepo;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    void createWarehouse_trimsFieldsAppliesDefaultsAndReturnsResponse() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                " Main Warehouse ",
                " Plot 12 ",
                " Mumbai ",
                null,
                200,
                "  ",
                " 9999 "
        );
        given(warehouseRepo.existsByName(" Main Warehouse ")).willReturn(false);
        given(warehouseRepo.save(any(Warehouse.class))).willAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            warehouse.setId(1L);
            return warehouse;
        });
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of());

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertThat(response.name()).isEqualTo("Main Warehouse");
        assertThat(response.country()).isEqualTo("India");
        assertThat(response.contactPhone()).isEqualTo("9999");
        assertThat(response.managerName()).isNull();
    }

    @Test
    void createWarehouse_duplicateNameThrows() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "Main Warehouse", "Plot 12", "Mumbai", "India", 100, null, null
        );
        given(warehouseRepo.existsByName("Main Warehouse")).willReturn(true);

        assertThatThrownBy(() -> warehouseService.createWarehouse(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(warehouseRepo, never()).save(any(Warehouse.class));
    }

    @Test
    void getAllWarehouses_respectsActiveOnlyFlag() {
        Warehouse active = warehouse(1L, "Active", true, 100);
        Warehouse inactive = warehouse(2L, "Inactive", false, 50);

        given(warehouseRepo.findByActiveTrue()).willReturn(List.of(active));
        given(warehouseRepo.findAll()).willReturn(List.of(active, inactive));
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of());
        given(stockRepo.findByWarehouseId(2L)).willReturn(List.of());

        assertThat(warehouseService.getAllWarehouses(true)).hasSize(1);
        assertThat(warehouseService.getAllWarehouses(false)).hasSize(2);
    }

    @Test
    void getWarehouseById_whenMissingThrows() {
        given(warehouseRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseService.getWarehouseById(99L))
                .isInstanceOf(WarehouseNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getWarehouseById_buildsRecommendationWhenCapacityTightens() {
        Warehouse primary = warehouse(1L, "Primary", true, 100);
        Warehouse backup = warehouse(2L, "Backup", true, 120);

        given(warehouseRepo.findById(1L)).willReturn(Optional.of(primary));
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of(
                stock(1L, 100L, 90, 10, 95),
                stock(1L, 101L, 5, 10, null)
        ));
        given(warehouseRepo.findByActiveTrue()).willReturn(List.of(primary, backup));
        given(stockRepo.sumQuantityByWarehouseId(2L)).willReturn(25);

        WarehouseResponse response = warehouseService.getWarehouseById(1L);

        assertThat(response.capacityPercent()).isEqualTo(95);
        assertThat(response.lowStockItemCount()).isEqualTo(1);
        assertThat(response.suggestedTransferWarehouseId()).isEqualTo(2L);
        assertThat(response.suggestedTransferFreeCapacity()).isEqualTo(95);
        assertThat(response.capacityAdvisory()).contains("Backup");
    }

    @Test
    void getWarehouseById_returnsNoRecommendationBelowThreshold() {
        Warehouse primary = warehouse(1L, "Primary", true, 200);

        given(warehouseRepo.findById(1L)).willReturn(Optional.of(primary));
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of(stock(1L, 100L, 40, 5, 100)));

        WarehouseResponse response = warehouseService.getWarehouseById(1L);

        assertThat(response.capacityPercent()).isEqualTo(20);
        assertThat(response.suggestedTransferWarehouseId()).isNull();
        assertThat(response.capacityAdvisory()).isNull();
    }

    @Test
    void getWarehouseById_returnsAdvisoryWithoutAlternativeWhenNoneAvailable() {
        Warehouse primary = warehouse(1L, "Primary", true, 100);
        Warehouse backup = warehouse(2L, "Backup", true, null);

        given(warehouseRepo.findById(1L)).willReturn(Optional.of(primary));
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of(stock(1L, 100L, 96, 10, 97)));
        given(warehouseRepo.findByActiveTrue()).willReturn(List.of(primary, backup));

        WarehouseResponse response = warehouseService.getWarehouseById(1L);

        assertThat(response.capacityPercent()).isEqualTo(96);
        assertThat(response.suggestedTransferWarehouseId()).isNull();
        assertThat(response.capacityAdvisory()).contains("no alternate active warehouse");
    }

    @Test
    void updateWarehouse_updatesChangedFieldsAndNormalizesBlanks() {
        Warehouse existing = warehouse(5L, "Old", true, 100);
        existing.setCountry("India");
        existing.setManagerName("Alice");

        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                " New Name ",
                " New Location ",
                " Pune ",
                " UAE ",
                500,
                "  ",
                " 12345 ",
                false
        );

        given(warehouseRepo.findById(5L)).willReturn(Optional.of(existing));
        given(warehouseRepo.existsByNameAndIdNot(" New Name ", 5L)).willReturn(false);
        given(warehouseRepo.save(existing)).willReturn(existing);
        given(stockRepo.findByWarehouseId(5L)).willReturn(List.of());

        WarehouseResponse response = warehouseService.updateWarehouse(5L, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.location()).isEqualTo("New Location");
        assertThat(response.city()).isEqualTo("Pune");
        assertThat(response.country()).isEqualTo("UAE");
        assertThat(response.totalStorageCapacity()).isEqualTo(500);
        assertThat(response.managerName()).isNull();
        assertThat(response.contactPhone()).isEqualTo("12345");
        assertThat(response.active()).isFalse();
    }

    @Test
    void updateWarehouse_duplicateRenamedWarehouseThrows() {
        Warehouse existing = warehouse(5L, "Old", true, 100);
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Taken", null, null, null, null, null, null, null
        );

        given(warehouseRepo.findById(5L)).willReturn(Optional.of(existing));
        given(warehouseRepo.existsByNameAndIdNot("Taken", 5L)).willReturn(true);

        assertThatThrownBy(() -> warehouseService.updateWarehouse(5L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deactivateAndReactivateWarehouseToggleActiveState() {
        Warehouse existing = warehouse(8L, "Warehouse", true, 100);

        given(warehouseRepo.findById(8L)).willReturn(Optional.of(existing));
        given(warehouseRepo.save(existing)).willReturn(existing);
        given(stockRepo.findByWarehouseId(8L)).willReturn(List.of());

        warehouseService.deactivateWarehouse(8L);
        assertThat(existing.isActive()).isFalse();

        WarehouseResponse response = warehouseService.reactivateWarehouse(8L);
        assertThat(existing.isActive()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void findActiveWarehouseOrThrow_rejectsInactiveWarehouse() {
        Warehouse existing = warehouse(3L, "Dormant", false, 100);
        given(warehouseRepo.findById(3L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> warehouseService.findActiveWarehouseOrThrow(3L))
                .isInstanceOf(WarehouseNotFoundException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void findActiveWarehouseOrThrow_returnsActiveWarehouse() {
        Warehouse existing = warehouse(3L, "Active", true, 100);
        given(warehouseRepo.findById(3L)).willReturn(Optional.of(existing));

        assertThat(warehouseService.findActiveWarehouseOrThrow(3L)).isSameAs(existing);
    }

    private Warehouse warehouse(Long id, String name, boolean active, Integer totalCapacity) {
        Warehouse warehouse = Warehouse.builder()
                .id(id)
                .name(name)
                .location("Loc")
                .city("City")
                .country("India")
                .active(active)
                .totalStorageCapacity(totalCapacity)
                .managerName("Manager")
                .contactPhone("9999")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
        warehouse.setCurrentCapacityUtilization(0);
        return warehouse;
    }

    private StockLevel stock(Long warehouseId, Long productId, int quantity, int reorderPoint, Integer maxCapacity) {
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("W-" + warehouseId).build();
        return StockLevel.builder()
                .warehouse(warehouse)
                .productId(productId)
                .productName("Product " + productId)
                .sku("SKU-" + productId)
                .quantity(quantity)
                .reorderPoint(reorderPoint)
                .maxCapacity(maxCapacity)
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }
}
