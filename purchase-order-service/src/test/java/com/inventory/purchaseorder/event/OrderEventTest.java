package com.inventory.purchaseorder.event;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventTest {

    @Test
    void builderAndNestedItemPreserveValues() {
        OrderEvent.ReceivedLineItem lineItem = OrderEvent.ReceivedLineItem.builder()
                .productId(10L)
                .productSku("SKU-1")
                .receivedQty(3)
                .build();
        OrderEvent event = OrderEvent.builder()
                .eventId("evt-1")
                .eventType("ORDER_CREATED")
                .purchaseOrderId(4L)
                .orderNumber("PO-4")
                .supplierId(5L)
                .warehouseId(6L)
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(BigDecimal.TEN)
                .receivedItems(List.of(lineItem))
                .triggeredBy("u1")
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getReceivedItems()).hasSize(1);
        assertThat(event.getReceivedItems().get(0).getProductSku()).isEqualTo("SKU-1");
    }
}
