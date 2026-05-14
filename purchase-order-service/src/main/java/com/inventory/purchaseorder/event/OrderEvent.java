package com.inventory.purchaseorder.event;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("java:S1068")
public class OrderEvent {

    /** UUID — consumers use this for idempotency. */
    private String eventId;

    private String eventType;  // ORDER_CREATED, ORDER_SUBMITTED, ORDER_APPROVED,
    // ORDER_RECEIVED, ORDER_PARTIALLY_RECEIVED, ORDER_CANCELLED

    private Long purchaseOrderId;
    private String orderNumber;
    private Long supplierId;
    private Long warehouseId;
    private PurchaseOrderStatus status;
    private BigDecimal totalAmount;

    // Populated only for RECEIVED / PARTIALLY_RECEIVED events
    // so Warehouse Service knows what stock to add
    private List<ReceivedLineItem> receivedItems;

    private String triggeredBy;   // userId who triggered the event
    private Instant occurredAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @SuppressWarnings("java:S1068")
    public static class ReceivedLineItem {
        private Long productId;
        private String productSku;
        private int receivedQty;
    }
}
