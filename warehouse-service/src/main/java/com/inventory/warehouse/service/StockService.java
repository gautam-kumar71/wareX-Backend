package com.inventory.warehouse.service;

import com.inventory.warehouse.dto.request.AdjustStockRequest;
import com.inventory.warehouse.dto.request.BulkThresholdUpdateRequest;
import com.inventory.warehouse.dto.request.TransferStockRequest;
import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.entity.StockLevel;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.enums.MovementType;
import com.inventory.warehouse.event.StockEvent;
import com.inventory.warehouse.exception.DuplicateStockEntryException;
import com.inventory.warehouse.exception.InsufficientStockException;
import com.inventory.warehouse.exception.StockLevelNotFoundException;
import com.inventory.warehouse.feign.ProductClient;
import com.inventory.warehouse.kafka.StockEventProducer;
import com.inventory.warehouse.mapper.StockMapper;
import com.inventory.warehouse.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockLevelRepository stockRepo;
    private final WarehouseService     warehouseService;
    private final StockEventProducer   eventProducer;
    private final StockMapper          stockMapper;
    private final ProductClient        productClient;

    // ─────────────────────────────────────────────────────────────────────────
    // Initialize stock for a product in a warehouse (first time only)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public StockLevelResponse initializeStock(Long warehouseId, Long productId,
                                              int initialQty, int reorderPoint,
                                              Integer maxCapacity) {
        // Guard: prevent duplicate (warehouse, product) entries
        if (stockRepo.existsByWarehouseIdAndProductId(warehouseId, productId)) {
            throw new DuplicateStockEntryException(warehouseId, productId);
        }

        Warehouse warehouse = warehouseService.findActiveWarehouseOrThrow(warehouseId);

        if (initialQty < 0) {
            throw new IllegalArgumentException("Initial quantity cannot be negative");
        }

        validateThresholdConfiguration(reorderPoint, maxCapacity);
        validateItemCapacityLimit(initialQty, maxCapacity, warehouse.getName(), productId, 0);
        validateWarehouseCapacityLimit(warehouse, initialQty, 0, productId);

        StockLevel stock = StockLevel.builder()
                .warehouse(warehouse)
                .productId(productId)
                .quantity(initialQty)
                .reservedQty(0)
                .reorderPoint(reorderPoint)
                .maxCapacity(maxCapacity)
                .build();
        enrichProductSnapshot(stock);

        updateWarehouseCapacity(warehouse, initialQty);

        StockLevel saved = stockRepo.save(stock);
        log.info("Stock initialized: warehouse={}, product={}, qty={}",
                warehouseId, productId, initialQty);

        // Publish initial stock event so Stock Movement Service records it
        if (initialQty > 0) {
            publishEvent(saved, MovementType.ADJUSTMENT_ADD, initialQty,
                    null, "INITIAL_STOCK");
        }

        return stockMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get stock level(s)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StockLevelResponse getStockLevel(Long warehouseId, Long productId) {
        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, productId));
        return stockMapper.toResponse(enrichProductSnapshot(stock));
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> getStockByWarehouse(Long warehouseId, String query,
                                                        boolean lowStockOnly, boolean overstockOnly,
                                                        String sortBy, String sortDir) {
        warehouseService.findActiveWarehouseOrThrow(warehouseId);
        return stockRepo.findByWarehouseId(warehouseId)
                .stream()
                .map(this::enrichProductSnapshot)
                .map(stockMapper::toResponse)
                .filter(stock -> matchesWarehouseFilters(stock, query, lowStockOnly, overstockOnly))
                .sorted(buildComparator(sortBy, sortDir))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> getStockByProduct(Long productId) {
        return stockRepo.findByProductId(productId)
                .stream()
                .map(this::enrichProductSnapshot)
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> getLowStockItems(Long warehouseId, String query,
                                                     String sortBy, String sortDir) {
        return stockRepo.findAllLowStock()
                .stream()
                .map(this::enrichProductSnapshot)
                .map(stockMapper::toResponse)
                .filter(stock -> warehouseId == null || warehouseId.equals(stock.warehouseId()))
                .filter(stock -> matchesSearch(stock, query))
                .sorted(buildComparator(sortBy, sortDir))
                .toList();
    }

    @Transactional(readOnly = true)
    public int getTotalStockForProduct(Long productId) {
        return stockRepo.sumQuantityByProductId(productId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adjust stock (manual or from purchase order receipt)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a signed quantity delta to a stock level.
     *
     * Positive delta  = adding stock (receipt, found stock, correction up)
     * Negative delta  = removing stock (sale, damage, shrinkage)
     *
     * Edge cases handled:
     *  - quantityDelta == 0 rejected
     *  - Resulting quantity < 0 rejected (InsufficientStockException)
     *  - Optimistic locking: @Version on StockLevel prevents lost updates
     *    under concurrent requests. Caller gets 409 CONFLICT to retry.
     */
    @Transactional
    public StockLevelResponse adjustStock(Long warehouseId, AdjustStockRequest req) {
        if (req.quantityDelta() == 0) {
            throw new IllegalArgumentException("Quantity delta cannot be zero");
        }

        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, req.productId())
                .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, req.productId()));

        int newQuantity = stock.getQuantity() + req.quantityDelta();

        // ── NEGATIVE STOCK PREVENTION ─────────────────────────────────────
        // Enforced here (service layer) AND at DB level (CHECK constraint).
        // Service check gives a meaningful error message; DB constraint is
        // the last line of defence against any bypass.
        if (newQuantity < 0) {
            throw new InsufficientStockException(
                    req.productId(), warehouseId,
                    Math.abs(req.quantityDelta()), stock.getQuantity());
        }

        if (req.quantityDelta() < 0 && newQuantity < stock.getReservedQty()) {
            throw new InsufficientStockException(
                    "Cannot reduce stock below the reserved quantity for product %d in warehouse %d. Reserved: %d, requested on-hand after adjustment: %d."
                            .formatted(req.productId(), warehouseId, stock.getReservedQty(), newQuantity));
        }

        boolean thresholdChangeRequested =
                (req.reorderPoint() != null && req.reorderPoint() != stock.getReorderPoint())
                        || (req.maxCapacity() != null && !Objects.equals(req.maxCapacity(), stock.getMaxCapacity()));
        if (thresholdChangeRequested && !currentUserCanManageThresholds()) {
            throw new AccessDeniedException("Only admins and inventory managers can change reorder points or max capacities.");
        }

        // Update thresholds if provided in the request
        int nextReorderPoint = req.reorderPoint() != null ? req.reorderPoint() : stock.getReorderPoint();
        Integer nextMaxCapacity = req.maxCapacity() != null ? req.maxCapacity() : stock.getMaxCapacity();
        validateThresholdConfiguration(nextReorderPoint, nextMaxCapacity);
        validateItemCapacityLimit(newQuantity, nextMaxCapacity, stock.getWarehouse().getName(), stock.getProductId(), stock.getQuantity());
        validateWarehouseCapacityLimit(stock.getWarehouse(), req.quantityDelta(), stock.getQuantity(), stock.getProductId());

        updateWarehouseCapacity(stock.getWarehouse(), req.quantityDelta());
        stock.setQuantity(newQuantity);

        if (req.reorderPoint() != null) stock.setReorderPoint(req.reorderPoint());
        if (req.maxCapacity()  != null) stock.setMaxCapacity(req.maxCapacity());

        // ── OPTIMISTIC LOCKING ────────────────────────────────────────────
        // Hibernate appends "AND version = ?" to the UPDATE statement.
        // If another transaction committed first, this throws
        // ObjectOptimisticLockingFailureException → handled by GlobalExceptionHandler
        // → 409 CONFLICT response → caller retries.
        StockLevel saved = stockRepo.save(stock);

        MovementType movementType = req.quantityDelta() > 0
                ? MovementType.ADJUSTMENT_ADD
                : MovementType.ADJUSTMENT_SUB;

        publishEvent(saved, movementType, req.quantityDelta(), null, req.reason().name());

        log.info("Stock adjusted: warehouse={}, product={}, delta={}, newQty={}",
                warehouseId, req.productId(), req.quantityDelta(), newQuantity);

        return stockMapper.toResponse(saved);
    }

    @Transactional
    public List<StockLevelResponse> bulkUpdateThresholds(Long warehouseId, BulkThresholdUpdateRequest req) {
        warehouseService.findActiveWarehouseOrThrow(warehouseId);

        if (req.reorderPoint() == null && req.maxCapacity() == null) {
            throw new IllegalArgumentException("Provide at least one threshold value to update");
        }

        return req.productIds().stream()
                .distinct()
                .map(productId -> {
                    StockLevel stock = stockRepo.findByWarehouseIdAndProductId(warehouseId, productId)
                            .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, productId));

                    int nextReorderPoint = req.reorderPoint() != null ? req.reorderPoint() : stock.getReorderPoint();
                    Integer nextMaxCapacity = req.maxCapacity() != null ? req.maxCapacity() : stock.getMaxCapacity();
                    validateThresholdConfiguration(nextReorderPoint, nextMaxCapacity);
                    validateItemCapacityLimit(stock.getQuantity(), nextMaxCapacity, stock.getWarehouse().getName(), stock.getProductId(), stock.getQuantity());

                    if (req.reorderPoint() != null) {
                        stock.setReorderPoint(req.reorderPoint());
                    }
                    if (req.maxCapacity() != null) {
                        stock.setMaxCapacity(req.maxCapacity());
                    }

                    return stockMapper.toResponse(stockRepo.save(stock));
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Receive stock from a purchase order
    // Called by Purchase Order Service via REST (OpenFeign)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public StockLevelResponse receiveStock(Long warehouseId, Long productId,
                                           int quantity, String purchaseOrderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Receipt quantity must be positive");
        }

        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> createReceiptStockLevel(warehouseId, productId));

        validateItemCapacityLimit(
                stock.getQuantity() + quantity,
                stock.getMaxCapacity(),
                stock.getWarehouse().getName(),
                stock.getProductId(),
                stock.getQuantity()
        );
        validateWarehouseCapacityLimit(stock.getWarehouse(), quantity, stock.getQuantity(), stock.getProductId());
        updateWarehouseCapacity(stock.getWarehouse(), quantity);
        stock.setQuantity(stock.getQuantity() + quantity);
        StockLevel saved = stockRepo.save(stock);

        publishEvent(saved, MovementType.RECEIPT, quantity, purchaseOrderId, "PURCHASE_ORDER");

        log.info("Stock received: warehouse={}, product={}, qty={}, PO={}",
                warehouseId, productId, quantity, purchaseOrderId);

        return stockMapper.toResponse(saved);
    }

    @Transactional
    public StockLevelResponse reverseReceivedStock(Long warehouseId, Long productId,
                                                   int quantity, String purchaseOrderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reversal quantity must be positive");
        }

        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, productId));

        int newQuantity = stock.getQuantity() - quantity;
        if (newQuantity < 0) {
            throw new InsufficientStockException(
                    "Cannot reverse %d units for product %d in warehouse %d because only %d units are currently on hand."
                            .formatted(quantity, productId, warehouseId, stock.getQuantity()));
        }

        if (newQuantity < stock.getReservedQty()) {
            throw new InsufficientStockException(
                    "Cannot reverse received stock below the reserved quantity for product %d in warehouse %d. Reserved: %d, requested on-hand after reversal: %d."
                            .formatted(productId, warehouseId, stock.getReservedQty(), newQuantity));
        }

        updateWarehouseCapacity(stock.getWarehouse(), -quantity);
        stock.setQuantity(newQuantity);
        StockLevel saved = stockRepo.save(stock);

        publishEvent(saved, MovementType.ADJUSTMENT_SUB, -quantity, purchaseOrderId, "PAYMENT_CANCELLATION");

        log.info("Received stock reversed: warehouse={}, product={}, qty={}, PO={}",
                warehouseId, productId, quantity, purchaseOrderId);

        return stockMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transfer stock between warehouses (atomic debit + credit)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transfers stock from one warehouse to another atomically.
     *
     * Both the debit (source) and credit (destination) happen inside a single
     * @Transactional boundary. If either fails, both roll back — no stock is
     * lost or duplicated.
     *
     * To prevent deadlocks when two transfers run concurrently in opposite
     * directions (A→B and B→A), we use PESSIMISTIC_WRITE locks acquired in
     * a deterministic order (lower warehouseId first).
     */
    @Transactional
    public void transferStock(TransferStockRequest req) {
        if (req.sourceWarehouseId().equals(req.destinationWarehouseId())) {
            throw new IllegalArgumentException(
                    "Source and destination warehouse cannot be the same");
        }
        if (req.quantity() <= 0) {
            throw new IllegalArgumentException("Transfer quantity must be positive");
        }

        // ── Validate both warehouses are active ───────────────────────────
        warehouseService.findActiveWarehouseOrThrow(req.sourceWarehouseId());
        Warehouse destinationWarehouse =
                warehouseService.findActiveWarehouseOrThrow(req.destinationWarehouseId());

        // ── Acquire locks in deterministic order to prevent deadlock ──────
        // If two transfers happen concurrently (A→B and B→A), both try to lock
        // the lower-id warehouse first → one blocks until the other finishes
        // → no circular wait → no deadlock.
        Long firstLockId  = Math.min(req.sourceWarehouseId(), req.destinationWarehouseId());
        Long secondLockId = Math.max(req.sourceWarehouseId(), req.destinationWarehouseId());

        StockLevel firstStock = getOrCreateTransferStock(firstLockId, req.productId(), destinationWarehouse);
        StockLevel secondStock = getOrCreateTransferStock(secondLockId, req.productId(), destinationWarehouse);

        // Map back to source/destination after lock acquisition
        StockLevel sourceStock = firstLockId.equals(req.sourceWarehouseId())
                ? firstStock : secondStock;
        StockLevel destStock   = firstLockId.equals(req.destinationWarehouseId())
                ? firstStock : secondStock;

        enrichProductSnapshot(sourceStock);
        if (destStock.getProductName() == null) {
            destStock.setProductName(sourceStock.getProductName());
        }
        if (destStock.getSku() == null) {
            destStock.setSku(sourceStock.getSku());
        }

        // ── Check source has enough available stock ────────────────────────
        if (sourceStock.getAvailableQuantity() < req.quantity()) {
            throw new InsufficientStockException(
                    req.productId(), req.sourceWarehouseId(),
                    req.quantity(), sourceStock.getAvailableQuantity());
        }

        // ── Atomic debit + credit ─────────────────────────────────────────
        updateWarehouseCapacity(sourceStock.getWarehouse(), -req.quantity());
        sourceStock.setQuantity(sourceStock.getQuantity() - req.quantity());

        validateItemCapacityLimit(
                destStock.getQuantity() + req.quantity(),
                destStock.getMaxCapacity(),
                destStock.getWarehouse().getName(),
                destStock.getProductId(),
                destStock.getQuantity()
        );
        validateWarehouseCapacityLimit(destStock.getWarehouse(), req.quantity(), destStock.getQuantity(), destStock.getProductId());
        updateWarehouseCapacity(destStock.getWarehouse(), req.quantity());
        destStock.setQuantity(destStock.getQuantity() + req.quantity());

        stockRepo.save(sourceStock);
        stockRepo.save(destStock);

        // ── Publish two events — one for each side of the transfer ────────
        // These are published AFTER the transaction commits (via @TransactionalEventListener
        // in a stricter setup). Here we publish directly — acceptable for this design.
        publishEvent(sourceStock, MovementType.TRANSFER_OUT, -req.quantity(),
                req.referenceId(), "TRANSFER");
        publishEvent(destStock, MovementType.TRANSFER_IN, req.quantity(),
                req.referenceId(), "TRANSFER");

        log.info("Stock transferred: product={}, qty={}, from={}, to={}",
                req.productId(), req.quantity(),
                req.sourceWarehouseId(), req.destinationWarehouseId());
    }

    private StockLevel getOrCreateTransferStock(Long warehouseId, Long productId, Warehouse destinationWarehouse) {
        return stockRepo.findByWarehouseIdAndProductIdForUpdate(warehouseId, productId)
                .orElseGet(() -> {
                    if (!warehouseId.equals(destinationWarehouse.getId())) {
                        throw new StockLevelNotFoundException(warehouseId, productId);
                    }
                    return stockRepo.save(StockLevel.builder()
                            .warehouse(destinationWarehouse)
                            .productId(productId)
                            .quantity(0)
                            .reservedQty(0)
                            .reorderPoint(0)
                            .build());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reserve stock for a pending order (soft lock)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public StockLevelResponse reserveStock(Long warehouseId, Long productId,
                                           int quantity, String orderId) {
        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, productId));

        if (stock.getAvailableQuantity() < quantity) {
            throw new InsufficientStockException(
                    productId, warehouseId, quantity, stock.getAvailableQuantity());
        }

        stock.setReservedQty(stock.getReservedQty() + quantity);
        StockLevel saved = stockRepo.save(stock);

        publishEvent(saved, MovementType.RESERVATION, quantity, orderId, "SALE_ORDER");

        log.info("Stock reserved: warehouse={}, product={}, qty={}, order={}",
                warehouseId, productId, quantity, orderId);

        return stockMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Release reservation (order cancelled)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public StockLevelResponse releaseReservation(Long warehouseId, Long productId,
                                                 int quantity, String orderId) {
        StockLevel stock = stockRepo
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new StockLevelNotFoundException(warehouseId, productId));

        int newReserved = stock.getReservedQty() - quantity;
        // Guard: reserved can never go below 0 (data integrity)
        stock.setReservedQty(Math.max(0, newReserved));
        StockLevel saved = stockRepo.save(stock);

        publishEvent(saved, MovementType.RESERVATION_RELEASE, quantity, orderId, "SALE_ORDER");

        log.info("Reservation released: warehouse={}, product={}, qty={}, order={}",
                warehouseId, productId, quantity, orderId);

        return stockMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updateWarehouseCapacity(Warehouse warehouse, int quantityDelta) {
        if (warehouse.getTotalStorageCapacity() != null && quantityDelta != 0) {
            int trackedUtilization = stockRepo.sumQuantityByWarehouseId(warehouse.getId());
            int newUtilization = trackedUtilization + quantityDelta;
            warehouse.setCurrentCapacityUtilization(Math.max(0, newUtilization));
        }
    }

    private void validateWarehouseCapacityLimit(Warehouse warehouse, int quantityDelta,
                                                int currentQuantity, Long productId) {
        if (warehouse.getTotalStorageCapacity() == null || quantityDelta <= 0) {
            return;
        }

        int trackedUtilization = stockRepo.sumQuantityByWarehouseId(warehouse.getId());
        int projectedUtilization = trackedUtilization + quantityDelta;
        if (projectedUtilization > warehouse.getTotalStorageCapacity()) {
            int remainingCapacity = Math.max(0, warehouse.getTotalStorageCapacity() - trackedUtilization);
            throw new IllegalStateException(
                    "Warehouse capacity exceeded for '%s'. Free tracked capacity: %d, requested inbound units: %d for product %d. Current on-hand for this SKU: %d."
                            .formatted(
                                    warehouse.getName(),
                                    remainingCapacity,
                                    quantityDelta,
                                    productId,
                                    currentQuantity
                            )
            );
        }
    }

    private boolean currentUserCanManageThresholds() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ROLE_INVENTORY_MANAGER"));
    }

    private void validateItemCapacityLimit(int nextQuantity, Integer maxCapacity,
                                           String warehouseName, Long productId, int currentQuantity) {
        if (maxCapacity == null) {
            return;
        }

        if (nextQuantity > maxCapacity) {
            throw new IllegalStateException(
                    "Product %d in warehouse '%s' cannot exceed its max capacity of %d. Current on-hand: %d, requested on-hand: %d."
                            .formatted(productId, warehouseName, maxCapacity, currentQuantity, nextQuantity)
            );
        }
    }

    private void publishEvent(StockLevel stock, MovementType type,
                              int quantityDelta, String referenceId, String referenceType) {
        enrichProductSnapshot(stock);

        StockEvent event = StockEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .movementType(type)
                .productId(stock.getProductId())
                .productName(resolveProductName(stock))
                .warehouseId(stock.getWarehouse().getId())
                .warehouseName(resolveWarehouseName(stock))
                .quantityDelta(quantityDelta)
                .newQuantity(stock.getQuantity())
                .availableQuantity(stock.getAvailableQuantity())
                .referenceId(referenceId)
                .referenceType(referenceType)
                .lowStock(stock.isLowStock())
                .overstock(stock.isOverstock())
                .occurredAt(Instant.now())
                .build();

        eventProducer.publishStockEvent(event);
    }

    private String resolveProductName(StockLevel stock) {
        if (stock.getProductName() != null && !stock.getProductName().isBlank()) {
            return stock.getProductName();
        }
        return "Product #" + stock.getProductId();
    }

    private String resolveWarehouseName(StockLevel stock) {
        String warehouseName = stock.getWarehouse() != null ? stock.getWarehouse().getName() : null;
        if (warehouseName != null && !warehouseName.isBlank()) {
            return warehouseName;
        }
        return "Warehouse #" + stock.getWarehouse().getId();
    }

    private StockLevel enrichProductSnapshot(StockLevel stock) {
        if (stock.getProductName() != null && stock.getSku() != null) {
            return stock;
        }

        try {
            var response = productClient.getProductById(stock.getProductId());
            if (response != null && response.data() != null) {
                var product = response.data();
                if (stock.getProductName() == null) {
                    stock.setProductName(product.name());
                }
                if (stock.getSku() == null) {
                    stock.setSku(product.sku());
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to resolve product snapshot for productId={}: {}", stock.getProductId(), ex.getMessage());
        }

        return stock;
    }

    private StockLevel createReceiptStockLevel(Long warehouseId, Long productId) {
        Warehouse warehouse = warehouseService.findActiveWarehouseOrThrow(warehouseId);

        StockLevel stock = StockLevel.builder()
                .warehouse(warehouse)
                .productId(productId)
                .quantity(0)
                .reservedQty(0)
                .reorderPoint(0)
                .build();

        enrichProductSnapshot(stock);
        log.info("Creating stock level on first receipt: warehouse={}, product={}",
                warehouseId, productId);
        return stockRepo.save(stock);
    }

    private boolean matchesWarehouseFilters(StockLevelResponse stock, String query,
                                            boolean lowStockOnly, boolean overstockOnly) {
        if (lowStockOnly && overstockOnly) {
            if (!stock.lowStock() && !stock.overstock()) {
                return false;
            }
        } else {
            if (lowStockOnly && !stock.lowStock()) {
                return false;
            }
            if (overstockOnly && !stock.overstock()) {
                return false;
            }
        }
        return matchesSearch(stock, query);
    }

    private boolean matchesSearch(StockLevelResponse stock, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return String.valueOf(stock.productId()).contains(normalized)
                || contains(stock.productName(), normalized)
                || contains(stock.sku(), normalized)
                || contains(stock.warehouseName(), normalized);
    }

    private boolean contains(String source, String query) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(query);
    }

    private void validateThresholdConfiguration(int reorderPoint, Integer maxCapacity) {
        if (reorderPoint < 0) {
            throw new IllegalArgumentException("Reorder point cannot be negative");
        }
        if (maxCapacity != null && maxCapacity <= 0) {
            throw new IllegalArgumentException("Max capacity must be greater than zero when provided");
        }
        if (maxCapacity != null && reorderPoint > 0 && maxCapacity <= reorderPoint) {
            throw new IllegalArgumentException("Max capacity must be greater than reorder point");
        }
    }

    private Comparator<StockLevelResponse> buildComparator(String sortBy, String sortDir) {
        Comparator<StockLevelResponse> comparator = switch (normalize(sortBy)) {
            case "product", "productname" -> Comparator.comparing(
                    stock -> normalizeNullable(stock.productName()),
                    String.CASE_INSENSITIVE_ORDER
            );
            case "sku" -> Comparator.comparing(
                    stock -> normalizeNullable(stock.sku()),
                    String.CASE_INSENSITIVE_ORDER
            );
            case "quantity", "onhand" -> Comparator.comparingInt(StockLevelResponse::quantity);
            case "available", "availableqty" -> Comparator.comparingInt(StockLevelResponse::availableQty);
            case "reserved", "reservedqty" -> Comparator.comparingInt(StockLevelResponse::reservedQty);
            case "reorder", "reorderpoint" -> Comparator.comparingInt(StockLevelResponse::reorderPoint);
            case "max", "maxcapacity" -> Comparator.comparing(
                    stock -> stock.maxCapacity() == null ? Integer.MAX_VALUE : stock.maxCapacity()
            );
            case "updated", "updatedat" -> Comparator.comparing(StockLevelResponse::updatedAt);
            default -> Comparator.comparing(StockLevelResponse::updatedAt);
        };

        return "asc".equals(normalize(sortDir)) ? comparator : comparator.reversed();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value;
    }
}
