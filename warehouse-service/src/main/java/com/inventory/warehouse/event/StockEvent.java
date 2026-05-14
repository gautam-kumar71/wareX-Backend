package com.inventory.warehouse.event;

import com.inventory.warehouse.enums.MovementType;
import lombok.*;

import java.time.Instant;

/**
 * Kafka event published to the "stock-events" topic after every stock change.
 *
 * Consumers:
 *   - Stock Movement Service  → inserts an immutable audit row
 *   - Alert Service           → evaluates low-stock / overstock rules
 *   - Report Service          → updates analytics projections
 *
 * eventId is a UUID used by consumers for idempotency checks.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("java:S1068")
public class StockEvent {

    /** UUID — consumers store this to detect and skip duplicate deliveries. */
    private String eventId;

    private MovementType movementType;

    private Long productId;

    private String productName;

    private Long warehouseId;

    private String warehouseName;

    /** Positive for additions, negative for removals. */
    private int quantityDelta;

    /** Stock quantity AFTER this change has been applied. */
    private int newQuantity;

    /** Available quantity (on-hand minus reserved) after the change. */
    private int availableQuantity;

    /**
     * ID of the business entity that triggered this change.
     * e.g. purchase order ID for a RECEIPT, or order ID for a SALE.
     * Null for manual adjustments.
     */
    private String referenceId;

    /** Type of entity referenced — "PURCHASE_ORDER", "SALE_ORDER", "MANUAL", etc. */
    private String referenceType;

    /** True when quantity <= reorderPoint after this change. */
    private boolean lowStock;

    /** True when quantity >= maxCapacity after this change. */
    private boolean overstock;

    private Instant occurredAt;
}
