package com.inventory.warehouse.service;

import com.inventory.warehouse.dto.request.AdjustStockRequest;
import com.inventory.warehouse.dto.request.BulkThresholdUpdateRequest;
import com.inventory.warehouse.dto.request.TransferStockRequest;
import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.entity.StockLevel;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.enums.AdjustmentReason;
import com.inventory.warehouse.enums.MovementType;
import com.inventory.warehouse.event.StockEvent;
import com.inventory.warehouse.exception.DuplicateStockEntryException;
import com.inventory.warehouse.exception.InsufficientStockException;
import com.inventory.warehouse.exception.StockLevelNotFoundException;
import com.inventory.warehouse.feign.ProductClient;
import com.inventory.warehouse.kafka.StockEventProducer;
import com.inventory.warehouse.mapper.StockMapper;
import com.inventory.warehouse.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock StockLevelRepository stockRepo;
    @Mock WarehouseService     warehouseService;
    @Mock StockEventProducer   eventProducer;
    @Mock StockMapper          stockMapper;
    @Mock ProductClient        productClient;
    @InjectMocks StockService  stockService;

    private Warehouse warehouse;
    private StockLevel stockLevel;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        warehouse = Warehouse.builder()
                .id(1L).name("Main Warehouse").location("Mumbai")
                .city("Mumbai").country("India").active(true).build();

        stockLevel = StockLevel.builder()
                .id(10L).warehouse(warehouse).productId(100L)
                .quantity(50).reservedQty(5).reorderPoint(10)
                .maxCapacity(200).version(0).build();
    }

    // ─── Initialize ───────────────────────────────────────────────────────────

    @Test
    void initializeStock_newProduct_savesStockLevel() {
        given(stockRepo.existsByWarehouseIdAndProductId(1L, 100L)).willReturn(false);
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(productClient.getProductById(100L)).willReturn(
                com.inventory.warehouse.dto.response.ApiResponse.success(
                        new ProductClient.ProductResponse(100L, "Product 100", "SKU-100")));
        given(stockMapper.toResponse(any())).willReturn(mockResponse(50));

        StockLevelResponse resp = stockService.initializeStock(1L, 100L, 50, 10, 200);

        assertThat(resp.quantity()).isEqualTo(50);
        verify(stockRepo).save(argThat(s ->
                s.getProductId().equals(100L) &&
                        s.getQuantity() == 50 &&
                        s.getReorderPoint() == 10));
        // Event published for non-zero initial qty
        verify(eventProducer).publishStockEvent(argThat(e ->
                e.getMovementType() == MovementType.ADJUSTMENT_ADD));
    }

    @Test
    void getStockByWarehouse_enrichesMissingSkuBeforeFiltering() {
        StockLevel stockWithoutSku = StockLevel.builder()
                .id(10L).warehouse(warehouse).productId(100L)
                .productName(null).sku(null)
                .quantity(50).reservedQty(5).reorderPoint(10)
                .maxCapacity(200).version(0).build();

        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(stockRepo.findByWarehouseId(1L)).willReturn(List.of(stockWithoutSku));
        given(productClient.getProductById(100L)).willReturn(
                com.inventory.warehouse.dto.response.ApiResponse.success(
                        new ProductClient.ProductResponse(100L, "Monitor", "M1-SKU")));
        given(stockMapper.toResponse(any())).willAnswer(inv -> {
            StockLevel stock = inv.getArgument(0);
            return new StockLevelResponse(
                    stock.getId(), stock.getWarehouse().getId(), stock.getWarehouse().getName(),
                    stock.getProductId(), stock.getProductName(), stock.getSku(),
                    stock.getQuantity(), stock.getReservedQty(), stock.getAvailableQuantity(),
                    stock.getReorderPoint(), stock.getMaxCapacity(), stock.isLowStock(),
                    stock.isOverstock(), java.time.Instant.now());
        });

        List<StockLevelResponse> results = stockService.getStockByWarehouse(1L, "M1", false, false, "updatedAt", "desc");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).sku()).isEqualTo("M1-SKU");
    }

    @Test
    void initializeStock_duplicate_throwsDuplicateStockEntryException() {
        given(stockRepo.existsByWarehouseIdAndProductId(1L, 100L)).willReturn(true);

        assertThatThrownBy(() -> stockService.initializeStock(1L, 100L, 0, 0, null))
                .isInstanceOf(DuplicateStockEntryException.class);
    }

    @Test
    void initializeStock_negativeQty_throwsIllegalArgument() {
        given(stockRepo.existsByWarehouseIdAndProductId(1L, 100L)).willReturn(false);
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);

        assertThatThrownBy(() -> stockService.initializeStock(1L, 100L, -5, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void initializeStock_invalidThresholds_throwsIllegalArgument() {
        given(stockRepo.existsByWarehouseIdAndProductId(1L, 100L)).willReturn(false);
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);

        assertThatThrownBy(() -> stockService.initializeStock(1L, 100L, 5, 5, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than reorder point");
    }

    @Test
    void initializeStock_zeroQty_doesNotPublishEvent() {
        given(stockRepo.existsByWarehouseIdAndProductId(1L, 101L)).willReturn(false);
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);

        StockLevel zeroStock = StockLevel.builder()
                .id(11L).warehouse(warehouse).productId(101L).quantity(0).build();
        given(stockRepo.save(any())).willReturn(zeroStock);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(0));

        stockService.initializeStock(1L, 101L, 0, 0, null);

        verify(eventProducer, never()).publishStockEvent(any());
    }

    @Test
    void initializeStock_rejectsQuantityBeyondWarehouseCapacity() {
        warehouse.setTotalStorageCapacity(10);
        warehouse.setCurrentCapacityUtilization(8);

        given(stockRepo.existsByWarehouseIdAndProductId(1L, 102L)).willReturn(false);
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(stockRepo.sumQuantityByWarehouseId(1L)).willReturn(8);

        assertThatThrownBy(() -> stockService.initializeStock(1L, 102L, 5, 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Warehouse capacity exceeded");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void getStockByProduct_returnsMappedResponses() {
        given(stockRepo.findByProductId(100L)).willReturn(List.of(stockLevel));
        given(productClient.getProductById(100L)).willReturn(
                com.inventory.warehouse.dto.response.ApiResponse.success(
                        new ProductClient.ProductResponse(100L, "Product 100", "SKU-100")));
        given(stockMapper.toResponse(any())).willReturn(mockResponse(50));

        List<StockLevelResponse> responses = stockService.getStockByProduct(100L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).productId()).isEqualTo(100L);
    }

    @Test
    void getLowStockItems_filtersAndSortsResults() {
        StockLevel lowA = StockLevel.builder()
                .id(11L).warehouse(warehouse).productId(100L).productName("Alpha").sku("A-1")
                .quantity(5).reservedQty(0).reorderPoint(10).maxCapacity(100).build();
        StockLevel lowB = StockLevel.builder()
                .id(12L).warehouse(warehouse).productId(101L).productName("Beta").sku("B-1")
                .quantity(3).reservedQty(0).reorderPoint(10).maxCapacity(100).build();

        given(stockRepo.findAllLowStock()).willReturn(List.of(lowA, lowB));
        given(stockMapper.toResponse(lowA)).willReturn(new StockLevelResponse(
                11L, 1L, "Main Warehouse", 100L, "Alpha", "A-1", 5, 0, 5, 10, 100, true, false, java.time.Instant.now()));
        given(stockMapper.toResponse(lowB)).willReturn(new StockLevelResponse(
                12L, 1L, "Main Warehouse", 101L, "Beta", "B-1", 3, 0, 3, 10, 100, true, false, java.time.Instant.now()));

        List<StockLevelResponse> responses = stockService.getLowStockItems(1L, "b", "availableQty", "asc");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).productName()).isEqualTo("Beta");
    }

    // ─── Adjust ───────────────────────────────────────────────────────────────

    @Test
    void adjustStock_positiveAddsStock_updatesAndPublishes() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(60));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.FOUND_STOCK, "Found extra boxes", null, null);

        stockService.adjustStock(1L, req);

        // Verify quantity was increased
        verify(stockRepo).save(argThat(s -> s.getQuantity() == 60));

        // Verify ADJUSTMENT_ADD event
        ArgumentCaptor<StockEvent> eventCaptor = ArgumentCaptor.forClass(StockEvent.class);
        verify(eventProducer).publishStockEvent(eventCaptor.capture());
        StockEvent event = eventCaptor.getValue();
        assertThat(event.getMovementType()).isEqualTo(MovementType.ADJUSTMENT_ADD);
        assertThat(event.getQuantityDelta()).isEqualTo(10);
        assertThat(event.getProductId()).isEqualTo(100L);
        assertThat(event.getWarehouseId()).isEqualTo(1L);
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    void adjustStock_negativeRemovesStock_updatesAndPublishes() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(40));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, -10, AdjustmentReason.DAMAGED_GOODS, "10 units damaged", null, null);

        stockService.adjustStock(1L, req);

        verify(stockRepo).save(argThat(s -> s.getQuantity() == 40));

        ArgumentCaptor<StockEvent> cap = ArgumentCaptor.forClass(StockEvent.class);
        verify(eventProducer).publishStockEvent(cap.capture());
        assertThat(cap.getValue().getMovementType()).isEqualTo(MovementType.ADJUSTMENT_SUB);
    }

    @Test
    void adjustStock_zeroDelta_throwsIllegalArgument() {
        AdjustStockRequest req = new AdjustStockRequest(
                100L, 0, AdjustmentReason.OTHER, null, null, null);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void adjustStock_wouldGoBelowZero_throwsInsufficientStock() {
        // quantity = 50, trying to remove 100
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, -100, AdjustmentReason.DAMAGED_GOODS, null, null, null);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        // Stock row must NOT be saved
        verify(stockRepo, never()).save(any());
        // No Kafka event must be published
        verify(eventProducer, never()).publishStockEvent(any());
    }

    @Test
    void adjustStock_exactlyZeroResult_succeeds() {
        // quantity = 50, removing exactly 50 → should succeed (0 is valid)
        stockLevel.setReservedQty(0);
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(0));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, -50, AdjustmentReason.CYCLE_COUNT, null, null, null);

        assertThatCode(() -> stockService.adjustStock(1L, req)).doesNotThrowAnyException();
        verify(stockRepo).save(argThat(s -> s.getQuantity() == 0));
    }

    @Test
    void adjustStock_productNotInWarehouse_throwsStockLevelNotFound() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 999L))
                .willReturn(Optional.empty());

        AdjustStockRequest req = new AdjustStockRequest(
                999L, 10, AdjustmentReason.OTHER, null, null, null);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(StockLevelNotFoundException.class);
    }

    @Test
    void adjustStock_updatesReorderPointIfProvided() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_MANAGER"))
                )
        );

        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(60));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.OTHER, null, 25, 500);

        stockService.adjustStock(1L, req);

        verify(stockRepo).save(argThat(s ->
                s.getReorderPoint() == 25 && s.getMaxCapacity() == 500));
    }

    @Test
    void adjustStock_positiveDeltaBeyondWarehouseCapacity_throwsIllegalState() {
        warehouse.setTotalStorageCapacity(55);

        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.sumQuantityByWarehouseId(1L)).willReturn(50);

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.FOUND_STOCK, "Found extra boxes", null, null);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Warehouse capacity exceeded");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void adjustStock_invalidThresholdUpdate_throwsIllegalArgument() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_MANAGER"))
                )
        );

        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.OTHER, null, 25, 25);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than reorder point");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void adjustStock_positiveAdd_rejectsQuantityBeyondWarehouseCapacity() {
        warehouse.setTotalStorageCapacity(55);
        warehouse.setCurrentCapacityUtilization(50);
        stockLevel.setWarehouse(warehouse);

        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.sumQuantityByWarehouseId(1L)).willReturn(50);

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.FOUND_STOCK, "Overflow receipt", null, null);

        assertThatThrownBy(() -> stockService.adjustStock(1L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Warehouse capacity exceeded");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void bulkUpdateThresholds_updatesDistinctProducts() {
        StockLevel secondStock = StockLevel.builder()
                .id(12L).warehouse(warehouse).productId(101L)
                .quantity(25).reservedQty(0).reorderPoint(5)
                .maxCapacity(150).version(0).build();

        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L)).willReturn(Optional.of(stockLevel));
        given(stockRepo.findByWarehouseIdAndProductId(1L, 101L)).willReturn(Optional.of(secondStock));
        given(stockRepo.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(stockMapper.toResponse(any())).willAnswer(inv -> {
            StockLevel stock = inv.getArgument(0);
            return new StockLevelResponse(
                    stock.getId(), stock.getWarehouse().getId(), stock.getWarehouse().getName(), stock.getProductId(),
                    stock.getProductName(), stock.getSku(), stock.getQuantity(), stock.getReservedQty(),
                    stock.getAvailableQuantity(), stock.getReorderPoint(), stock.getMaxCapacity(),
                    stock.isLowStock(), stock.isOverstock(), java.time.Instant.now());
        });

        List<StockLevelResponse> responses = stockService.bulkUpdateThresholds(
                1L, new BulkThresholdUpdateRequest(List.of(100L, 101L, 100L), 20, 250));

        assertThat(responses).hasSize(2);
        verify(stockRepo, times(2)).save(any());
        assertThat(stockLevel.getReorderPoint()).isEqualTo(20);
        assertThat(secondStock.getMaxCapacity()).isEqualTo(250);
    }

    @Test
    void bulkUpdateThresholds_requiresAtLeastOneValue() {
        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);

        assertThatThrownBy(() -> stockService.bulkUpdateThresholds(
                1L, new BulkThresholdUpdateRequest(List.of(100L), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one threshold");
    }

    @Test
    void receiveStock_existingAndNewRow_pathsWork() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L)).willReturn(Optional.of(stockLevel));
        given(stockRepo.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(stockMapper.toResponse(any())).willReturn(mockResponse(55));

        StockLevelResponse existing = stockService.receiveStock(1L, 100L, 5, "PO-1");
        assertThat(existing.quantity()).isEqualTo(55);

        Warehouse secondWarehouse = Warehouse.builder()
                .id(2L).name("Second Warehouse").location("Pune")
                .city("Pune").country("India").active(true).build();
        given(stockRepo.findByWarehouseIdAndProductId(2L, 200L)).willReturn(Optional.empty());
        given(warehouseService.findActiveWarehouseOrThrow(2L)).willReturn(secondWarehouse);
        given(productClient.getProductById(200L)).willReturn(
                com.inventory.warehouse.dto.response.ApiResponse.success(
                        new ProductClient.ProductResponse(200L, "Product 200", "SKU-200")));
        given(stockMapper.toResponse(any())).willAnswer(inv -> {
            StockLevel stock = inv.getArgument(0);
            return new StockLevelResponse(
                    stock.getId(), stock.getWarehouse().getId(), stock.getWarehouse().getName(), stock.getProductId(),
                    stock.getProductName(), stock.getSku(), stock.getQuantity(), stock.getReservedQty(),
                    stock.getAvailableQuantity(), stock.getReorderPoint(), stock.getMaxCapacity(),
                    stock.isLowStock(), stock.isOverstock(), java.time.Instant.now());
        });

        StockLevelResponse created = stockService.receiveStock(2L, 200L, 7, "PO-2");
        assertThat(created.productName()).isEqualTo("Product 200");
        assertThat(created.quantity()).isEqualTo(7);
        verify(eventProducer, atLeast(2)).publishStockEvent(any());
    }

    @Test
    void receiveStock_invalidQuantity_throwsIllegalArgument() {
        assertThatThrownBy(() -> stockService.receiveStock(1L, 100L, 0, "PO-0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ─── Transfer ─────────────────────────────────────────────────────────────

    @Test
    void transferStock_validTransfer_debitsSourceCreditsDestination() {
        Warehouse destWarehouse = Warehouse.builder()
                .id(2L).name("North Hub").active(true).build();

        StockLevel sourceStock = StockLevel.builder()
                .id(10L).warehouse(warehouse).productId(100L)
                .quantity(50).reservedQty(0).version(0).build();
        StockLevel destStock = StockLevel.builder()
                .id(11L).warehouse(destWarehouse).productId(100L)
                .quantity(20).reservedQty(0).version(0).build();

        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(warehouseService.findActiveWarehouseOrThrow(2L)).willReturn(destWarehouse);
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(1L, 100L))
                .willReturn(Optional.of(sourceStock));
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(2L, 100L))
                .willReturn(Optional.of(destStock));
        given(stockRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 30, "TXR-001");
        stockService.transferStock(req);

        assertThat(sourceStock.getQuantity()).isEqualTo(20);  // 50 - 30
        assertThat(destStock.getQuantity()).isEqualTo(50);    // 20 + 30

        // Two events published — one TRANSFER_OUT, one TRANSFER_IN
        ArgumentCaptor<StockEvent> cap = ArgumentCaptor.forClass(StockEvent.class);
        verify(eventProducer, times(2)).publishStockEvent(cap.capture());
        List<StockEvent> events = cap.getAllValues();
        assertThat(events).extracting(StockEvent::getMovementType)
                .containsExactlyInAnyOrder(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);
    }

    @Test
    void transferStock_missingDestinationStock_createsRowAndTransfers() {
        Warehouse destWarehouse = Warehouse.builder()
                .id(2L).name("North Hub").active(true).build();

        StockLevel sourceStock = StockLevel.builder()
                .id(10L).warehouse(warehouse).productId(100L).productName("Laptop")
                .sku("LAP-100").quantity(50).reservedQty(0).version(0).build();

        given(warehouseService.findActiveWarehouseOrThrow(1L)).willReturn(warehouse);
        given(warehouseService.findActiveWarehouseOrThrow(2L)).willReturn(destWarehouse);
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(1L, 100L))
                .willReturn(Optional.of(sourceStock));
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(2L, 100L))
                .willReturn(Optional.empty());
        given(stockRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 15, "TXR-NEW");
        stockService.transferStock(req);

        ArgumentCaptor<StockLevel> savedStockCaptor = ArgumentCaptor.forClass(StockLevel.class);
        verify(stockRepo, atLeast(2)).save(savedStockCaptor.capture());

        StockLevel destinationStock = savedStockCaptor.getAllValues().stream()
                .filter(stock -> stock.getWarehouse().getId().equals(2L))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(sourceStock.getQuantity()).isEqualTo(35);
        assertThat(destinationStock.getQuantity()).isEqualTo(15);
        assertThat(destinationStock.getProductName()).isEqualTo("Laptop");
        assertThat(destinationStock.getSku()).isEqualTo("LAP-100");
    }

    @Test
    void transferStock_sameSourceAndDest_throwsIllegalArgument() {
        TransferStockRequest req = new TransferStockRequest(100L, 1L, 1L, 10, null);

        assertThatThrownBy(() -> stockService.transferStock(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same");
    }

    @Test
    void transferStock_insufficientAvailableStock_throwsInsufficientStock() {
        // quantity=50 but reservedQty=40, so available=10. Trying to transfer 20.
        stockLevel.setReservedQty(40); // available = 50 - 40 = 10

        Warehouse destWarehouse = Warehouse.builder().id(2L).active(true).build();
        StockLevel destStock = StockLevel.builder()
                .id(11L).warehouse(destWarehouse).productId(100L).quantity(5).build();

        given(warehouseService.findActiveWarehouseOrThrow(anyLong())).willReturn(warehouse);
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(1L, 100L))
                .willReturn(Optional.of(stockLevel));
        given(stockRepo.findByWarehouseIdAndProductIdForUpdate(2L, 100L))
                .willReturn(Optional.of(destStock));

        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 20, null);

        assertThatThrownBy(() -> stockService.transferStock(req))
                .isInstanceOf(InsufficientStockException.class);

        // Neither row should be saved
        verify(stockRepo, never()).save(any());
    }

    @Test
    void transferStock_zeroQuantity_throwsIllegalArgument() {
        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 0, null);

        assertThatThrownBy(() -> stockService.transferStock(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ─── Reserve / Release ────────────────────────────────────────────────────

    @Test
    void reserveStock_sufficientAvailable_increasesReservedQty() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel)); // qty=50, reserved=5, available=45
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(50));

        stockService.reserveStock(1L, 100L, 10, "ORDER-001");

        verify(stockRepo).save(argThat(s -> s.getReservedQty() == 15)); // 5 + 10
    }

    @Test
    void reserveStock_moreThanAvailable_throwsInsufficientStock() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel)); // available = 50 - 5 = 45

        assertThatThrownBy(() -> stockService.reserveStock(1L, 100L, 50, "ORDER-001"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void releaseReservation_releasesCorrectAmount() {
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel)); // reservedQty = 5
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(50));

        stockService.releaseReservation(1L, 100L, 5, "ORDER-001");

        verify(stockRepo).save(argThat(s -> s.getReservedQty() == 0));
    }

    @Test
    void releaseReservation_releasingMoreThanReserved_clampsToZero() {
        // Guard: reservedQty can never go below 0 even with bad data
        given(stockRepo.findByWarehouseIdAndProductId(1L, 100L))
                .willReturn(Optional.of(stockLevel)); // reservedQty = 5
        given(stockRepo.save(any())).willReturn(stockLevel);
        given(stockMapper.toResponse(any())).willReturn(mockResponse(50));

        stockService.releaseReservation(1L, 100L, 100, "ORDER-001");

        verify(stockRepo).save(argThat(s -> s.getReservedQty() == 0)); // clamped, not -95
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private StockLevelResponse mockResponse(int qty) {
        return new StockLevelResponse(
                10L, 1L, "Main Warehouse", 100L,
                "Product 100", "SKU-100",
                qty, 5, Math.max(0, qty - 5), 10, 200,
                qty <= 10, qty >= 200, java.time.Instant.now());
    }
}
