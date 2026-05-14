package com.inventory.purchaseorder.entity;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // Auto-generated order number: PO-YYYYMMDD-XXXXX
    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    // Supplier reference — stored locally to avoid Feign call on every read
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    // Destination warehouse for the received goods
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Audit fields — userId from JWT X-User-Id header
    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "cancelled_by", length = 36)
    private String cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

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

    public void addLine(PurchaseOrderLine line) {
        lines.add(line);
        line.setPurchaseOrder(this);
        recalculateTotal();
    }

    public void recalculateTotal() {
        this.totalAmount = lines.stream()
                .map(PurchaseOrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isFullyReceived() {
        return !lines.isEmpty() &&
                lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
    }

    public boolean isPartiallyReceived() {
        return lines.stream().anyMatch(l -> l.getReceivedQty() > 0)
                && !isFullyReceived();
    }
}