package com.inventory.purchaseorder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    // Product reference — stored locally (no shared DB with Product Service)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;

    @Column(name = "ordered_qty", nullable = false)
    private int orderedQty;

    @Column(name = "received_qty", nullable = false)
    @Builder.Default
    private int receivedQty = 0;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        recalculateLineTotal();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void recalculateLineTotal() {
        if (unitPrice != null) {
            this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(orderedQty));
        }
    }

    public boolean isFullyReceived() {
        return receivedQty >= orderedQty;
    }

    public int getRemainingQty() {
        return orderedQty - receivedQty;
    }
}