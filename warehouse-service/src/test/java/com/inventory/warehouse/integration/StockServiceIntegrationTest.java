package com.inventory.warehouse.integration;

import com.inventory.warehouse.dto.request.AdjustStockRequest;
import com.inventory.warehouse.entity.StockLevel;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.enums.AdjustmentReason;
import com.inventory.warehouse.exception.InsufficientStockException;
import com.inventory.warehouse.kafka.StockEventProducer;
import com.inventory.warehouse.repository.StockLevelRepository;
import com.inventory.warehouse.repository.WarehouseRepository;
import com.inventory.warehouse.service.StockService;
import com.inventory.warehouse.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class StockServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("warehouse_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("jwt.secret",
                () -> "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    @Autowired StockService         stockService;
    @Autowired WarehouseService     warehouseService;
    @Autowired StockLevelRepository stockRepo;
    @Autowired WarehouseRepository  warehouseRepo;

    // Mock Kafka so we don't need a real broker for integration tests
    @MockBean StockEventProducer eventProducer;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        stockRepo.deleteAll();
        warehouseRepo.deleteAll();
        doNothing().when(eventProducer).publishStockEvent(any());

        warehouse = Warehouse.builder()
                .name("Test Warehouse")
                .location("Test Location")
                .city("Test City")
                .country("India")
                .active(true)
                .build();
        warehouse = warehouseRepo.save(warehouse);
    }

    // ─── Core: negative stock prevention ──────────────────────────────────────

    @Test
    void adjustStock_cannotGoBelowZero_rejectsExcessiveRemoval() {
        stockService.initializeStock(warehouse.getId(), 1L, 10, 0, null);

        AdjustStockRequest req = new AdjustStockRequest(
                1L, -20, AdjustmentReason.DAMAGED_GOODS, null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> stockService.adjustStock(warehouse.getId(), req))
                .isInstanceOf(InsufficientStockException.class);

        // DB must still show original quantity
        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouse.getId(), 1L).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    // ─── RACE CONDITION: concurrent decrements ─────────────────────────────────
    //
    // Scenario: 10 threads simultaneously try to remove 1 unit each from a
    // stock of 5. Exactly 5 should succeed; exactly 5 should be rejected.
    // No thread should succeed in taking the quantity below 0.
    //
    // This test validates BOTH:
    //   1. Optimistic locking prevents lost updates (no corruption)
    //   2. The final quantity is exactly 0 (not negative)

    @Test
    void adjustStock_concurrentDecrements_preventNegativeStock() throws Exception {
        int initialQty      = 5;
        int threadCount     = 10;
        int decrementAmount = 1;

        stockService.initializeStock(warehouse.getId(), 2L, initialQty, 0, null);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1); // all threads start simultaneously
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> errors     = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await(); // wait for all threads to be ready
                    AdjustStockRequest req = new AdjustStockRequest(
                            2L, -decrementAmount, AdjustmentReason.OTHER, null, null, null);
                    stockService.adjustStock(warehouse.getId(), req);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException |
                         org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                    failureCount.incrementAndGet(); // expected for some threads
                } catch (Exception ex) {
                    errors.add(ex); // unexpected — should not happen
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // No unexpected exceptions
        assertThat(errors).isEmpty();

        // Final DB state
        StockLevel finalStock = stockRepo
                .findByWarehouseIdAndProductId(warehouse.getId(), 2L).orElseThrow();

        // Quantity must NEVER be negative
        assertThat(finalStock.getQuantity()).isGreaterThanOrEqualTo(0);

        // Exactly initialQty threads should have succeeded (no more, no less)
        assertThat(successCount.get()).isEqualTo(initialQty);
        assertThat(failureCount.get()).isEqualTo(threadCount - initialQty);

        // Final quantity must be exactly 0
        assertThat(finalStock.getQuantity()).isEqualTo(0);
    }

    // ─── RACE CONDITION: concurrent transfers ──────────────────────────────────
    //
    // Scenario: Two transfers happen simultaneously in opposite directions.
    // A→B and B→A. Deadlock-safe locking must prevent a deadlock.
    // After both complete, total stock across both warehouses must be unchanged.

    @Test
    void transferStock_concurrentOppositeDirections_noDeadlock() throws Exception {
        Warehouse warehouseB = Warehouse.builder()
                .name("Warehouse B").location("B").city("Delhi")
                .country("India").active(true).build();
        warehouseB = warehouseRepo.save(warehouseB);

        stockService.initializeStock(warehouse.getId(),  3L, 100, 0, null);
        stockService.initializeStock(warehouseB.getId(), 3L, 100, 0, null);

        final Long warehouseAId = warehouse.getId();
        final Long warehouseBId = warehouseB.getId();

        ExecutorService pool     = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(2);
        List<Exception> errors    = new CopyOnWriteArrayList<>();

        // Thread 1: transfer 10 from A → B
        pool.submit(() -> {
            try {
                startLatch.await();
                com.inventory.warehouse.dto.request.TransferStockRequest req =
                        new com.inventory.warehouse.dto.request.TransferStockRequest(
                                3L, warehouseAId, warehouseBId, 10, "T1");
                stockService.transferStock(req);
            } catch (Exception ex) { errors.add(ex); }
            finally { doneLatch.countDown(); }
        });

        // Thread 2: transfer 10 from B → A
        pool.submit(() -> {
            try {
                startLatch.await();
                com.inventory.warehouse.dto.request.TransferStockRequest req =
                        new com.inventory.warehouse.dto.request.TransferStockRequest(
                                3L, warehouseBId, warehouseAId, 10, "T2");
                stockService.transferStock(req);
            } catch (Exception ex) { errors.add(ex); }
            finally { doneLatch.countDown(); }
        });

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);

        pool.shutdown();

        // Both threads must complete within timeout (no deadlock)
        assertThat(finished).as("Deadlock detected — transfers did not complete").isTrue();
        assertThat(errors).isEmpty();

        // Total stock across both warehouses must still be 200 (no stock lost or created)
        int totalA = stockRepo
                .findByWarehouseIdAndProductId(warehouseAId, 3L).orElseThrow().getQuantity();
        int totalB = stockRepo
                .findByWarehouseIdAndProductId(warehouseBId, 3L).orElseThrow().getQuantity();
        assertThat(totalA + totalB).isEqualTo(200);
    }

    // ─── Low stock detection ───────────────────────────────────────────────────

    @Test
    void getLowStockItems_belowReorderPoint_appearsInList() {
        stockService.initializeStock(warehouse.getId(), 4L, 5, 10, null); // 5 < reorderPoint 10

        List<com.inventory.warehouse.dto.response.StockLevelResponse> lowStock =
                stockService.getLowStockItems(null, null, "availableQty", "desc");

        assertThat(lowStock).anyMatch(s ->
                s.productId().equals(4L) && s.lowStock());
    }

    @Test
    void getLowStockItems_aboveReorderPoint_doesNotAppear() {
        stockService.initializeStock(warehouse.getId(), 5L, 50, 10, null); // 50 > reorderPoint 10

        List<com.inventory.warehouse.dto.response.StockLevelResponse> lowStock =
                stockService.getLowStockItems(null, null, "availableQty", "desc");

        assertThat(lowStock).noneMatch(s -> s.productId().equals(5L));
    }

    @Test
    void warehouseCapacity_isDerivedFromTrackedStockQuantities() {
        warehouse.setTotalStorageCapacity(20);
        warehouseRepo.save(warehouse);

        stockService.initializeStock(warehouse.getId(), 6L, 7, 0, null);
        stockService.initializeStock(warehouse.getId(), 7L, 5, 0, null);

        var response = warehouseService.getWarehouseById(warehouse.getId());

        assertThat(response.currentCapacityUtilization()).isEqualTo(12);
        assertThat(response.capacityPercent()).isEqualTo(60);
    }

    @Test
    void warehouseCapacity_canBeExceededWhileTrackedUtilizationStillUpdates() {
        warehouse.setTotalStorageCapacity(10);
        warehouseRepo.save(warehouse);

        stockService.initializeStock(warehouse.getId(), 8L, 7, 0, null);
        stockService.initializeStock(warehouse.getId(), 9L, 6, 0, null);

        var response = warehouseService.getWarehouseById(warehouse.getId());

        assertThat(response.currentCapacityUtilization()).isEqualTo(13);
        assertThat(response.capacityPercent()).isEqualTo(100);
    }
}
