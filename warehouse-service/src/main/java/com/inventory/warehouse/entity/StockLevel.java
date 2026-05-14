package com.inventory.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stock_levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // product_id is a foreign reference to Product Service — NOT a JPA FK.
    // Microservices do NOT share databases, so we store just the ID.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Cached Product Info for UI performance
    @Column(name = "product_name")
    private String productName;

    @Column(name = "sku")
    private String sku;

    // Exact physical location
    @Column(name = "aisle", length = 50)
    private String aisle;

    @Column(name = "rack", length = 50)
    private String rack;

    @Column(name = "bin", length = 50)
    private String bin;

    // Batch & Expiry tracking
    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    // Current on-hand stock quantity.
    // DB CHECK constraint + service-level guard both prevent this going below 0.
    @Column(nullable = false)
    @Builder.Default
    private int quantity = 0;

    // Qty reserved for pending/approved orders not yet shipped.
    // Available stock = quantity - reservedQty
    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private int reservedQty = 0;

    // When quantity drops AT OR BELOW this value, a LOW_STOCK alert is fired.
    @Column(name = "reorder_point", nullable = false)
    @Builder.Default
    private int reorderPoint = 0;

    // When quantity rises AT OR ABOVE this value, an OVERSTOCK alert is fired.
    // NULL means no overstock threshold is set for this product/warehouse.
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    // ── OPTIMISTIC LOCKING ─────────────────────────────────────────────────
    // @Version tells Hibernate to include "WHERE version = ?" in every UPDATE.
    // If two transactions read the same row and both try to update it, the
    // second one will fail with OptimisticLockException — preventing lost updates.
    // The service layer catches this and retries or rejects the request.
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // ── Business helpers ───────────────────────────────────────────────────

    /**
     * Available quantity = on-hand minus what's reserved for pending orders.
     */
    public int getAvailableQuantity() {
        return Math.max(0, quantity - reservedQty);
    }

    /**
     * Whether this stock level is at or below its reorder point.
     */
    public boolean isLowStock() {
        return reorderPoint > 0
                && quantity <= reorderPoint
                && (maxCapacity == null || quantity < maxCapacity);
    }

    /**
     * Whether this stock level is at or above its max capacity.
     */
    public boolean isOverstock() {
        return maxCapacity != null && quantity >= maxCapacity;
    }
}
