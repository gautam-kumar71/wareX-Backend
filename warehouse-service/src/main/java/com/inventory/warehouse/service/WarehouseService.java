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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseService {

    private final WarehouseRepository warehouseRepo;
    private final StockLevelRepository stockRepo;
    private final WarehouseMapper     warehouseMapper;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest req) {
        if (warehouseRepo.existsByName(req.name())) {
            throw new IllegalArgumentException(
                    "A warehouse with name '" + req.name() + "' already exists");
        }

        Warehouse warehouse = Warehouse.builder()
                .name(req.name().trim())
                .location(req.location().trim())
                .city(req.city().trim())
                .country(req.country() != null ? req.country().trim() : "India")
                .totalStorageCapacity(req.totalStorageCapacity())
                .managerName(normalizeNullable(req.managerName()))
                .contactPhone(normalizeNullable(req.contactPhone()))
                .active(true)
                .build();

        Warehouse saved = warehouseRepo.save(warehouse);
        log.info("Warehouse created: id={}, name={}", saved.getId(), saved.getName());
        return buildResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses(boolean activeOnly) {
        List<Warehouse> warehouses = activeOnly
                ? warehouseRepo.findByActiveTrue()
                : warehouseRepo.findAll();
        return warehouses.stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        return warehouseRepo.findById(id)
                .map(this::buildResponse)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest req) {
        Warehouse warehouse = warehouseRepo.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));

        // Reject name change if the new name is already taken by a different warehouse
        if (req.name() != null && !req.name().equals(warehouse.getName())) {
            if (warehouseRepo.existsByNameAndIdNot(req.name(), id)) {
                throw new IllegalArgumentException(
                        "A warehouse with name '" + req.name() + "' already exists");
            }
            warehouse.setName(req.name().trim());
        }

        if (req.location() != null) warehouse.setLocation(req.location().trim());
        if (req.city()     != null) warehouse.setCity(req.city().trim());
        if (req.country()  != null) warehouse.setCountry(req.country().trim());
        if (req.totalStorageCapacity() != null) warehouse.setTotalStorageCapacity(req.totalStorageCapacity());
        if (req.managerName() != null) warehouse.setManagerName(normalizeNullable(req.managerName()));
        if (req.contactPhone() != null) warehouse.setContactPhone(normalizeNullable(req.contactPhone()));
        if (req.active()   != null) warehouse.setActive(req.active());

        Warehouse updated = warehouseRepo.save(warehouse);
        log.info("Warehouse updated: id={}", updated.getId());
        return buildResponse(updated);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Transactional
    public void deactivateWarehouse(Long id) {
        Warehouse warehouse = warehouseRepo.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
        warehouse.setActive(false);
        warehouseRepo.save(warehouse);
        log.info("Warehouse deactivated: id={}", id);
    }

    @Transactional
    public WarehouseResponse reactivateWarehouse(Long id) {
        Warehouse warehouse = warehouseRepo.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
        warehouse.setActive(true);
        Warehouse updated = warehouseRepo.save(warehouse);
        log.info("Warehouse reactivated: id={}", id);
        return buildResponse(updated);
    }

    // ── Helper used by StockService ───────────────────────────────────────────

    public Warehouse findActiveWarehouseOrThrow(Long id) {
        Warehouse warehouse = warehouseRepo.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
        if (!warehouse.isActive()) {
            throw new WarehouseNotFoundException(
                    "Warehouse " + id + " exists but is inactive");
        }
        return warehouse;
    }

    private WarehouseResponse buildResponse(Warehouse warehouse) {
        List<StockLevel> stockLevels = stockRepo.findByWarehouseId(warehouse.getId());
        int lowStockCount = (int) stockLevels.stream().filter(StockLevel::isLowStock).count();
        int overstockCount = (int) stockLevels.stream().filter(StockLevel::isOverstock).count();
        int trackedUtilization = stockLevels.stream().mapToInt(StockLevel::getQuantity).sum();

        Integer capacityPercent = null;
        if (warehouse.getTotalStorageCapacity() != null && warehouse.getTotalStorageCapacity() > 0) {
            capacityPercent = Math.min(100, Math.round((trackedUtilization * 100f) / warehouse.getTotalStorageCapacity()));
        }

        TransferRecommendation recommendation = buildTransferRecommendation(warehouse, capacityPercent, trackedUtilization);

        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.getCity(),
                warehouse.getCountry(),
                warehouse.getTotalStorageCapacity(),
                trackedUtilization,
                capacityPercent,
                lowStockCount,
                overstockCount,
                warehouse.getManagerName(),
                warehouse.getContactPhone(),
                recommendation.suggestedWarehouseId(),
                recommendation.suggestedWarehouseName(),
                recommendation.suggestedFreeCapacity(),
                recommendation.advisory(),
                warehouse.isActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }

    private TransferRecommendation buildTransferRecommendation(Warehouse warehouse, Integer capacityPercent, int trackedUtilization) {
        if (capacityPercent == null || capacityPercent < 85) {
            return new TransferRecommendation(null, null, null, null);
        }

        List<Warehouse> activeWarehouses = warehouseRepo.findByActiveTrue();
        Warehouse bestAlternative = activeWarehouses.stream()
                .filter(candidate -> !candidate.getId().equals(warehouse.getId()))
                .filter(candidate -> candidate.getTotalStorageCapacity() != null)
                .filter(candidate -> getFreeCapacity(candidate) > 0)
                .max(java.util.Comparator.comparingInt(this::getFreeCapacity))
                .orElse(null);

        if (bestAlternative == null) {
            String advisory = capacityPercent >= 95
                ? "Warehouse is near full and no alternate active warehouse currently has spare tracked capacity."
                    : "Warehouse is under capacity pressure. No alternate active warehouse with tracked spare capacity was found.";
            return new TransferRecommendation(null, null, null, advisory);
        }

        String advisory = capacityPercent >= 95
                ? "Warehouse is near full. Transfer outbound or slow-moving stock to %s."
                    .formatted(bestAlternative.getName())
                : "Capacity is tightening. %s is the best transfer candidate if stock needs rebalancing."
                    .formatted(bestAlternative.getName());

        return new TransferRecommendation(
                bestAlternative.getId(),
                bestAlternative.getName(),
                getFreeCapacity(bestAlternative),
                advisory
        );
    }

    private int getFreeCapacity(Warehouse warehouse) {
        if (warehouse.getTotalStorageCapacity() == null) {
            return 0;
        }
        int used = stockRepo.sumQuantityByWarehouseId(warehouse.getId());
        return Math.max(0, warehouse.getTotalStorageCapacity() - used);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record TransferRecommendation(
            Long suggestedWarehouseId,
            String suggestedWarehouseName,
            Integer suggestedFreeCapacity,
            String advisory
    ) {}
}
