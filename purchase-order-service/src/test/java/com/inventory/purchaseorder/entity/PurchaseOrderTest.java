package com.inventory.purchaseorder.entity;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderTest {

    @Test
    void lifecycleAndBusinessHelpers_work() {
        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber("PO-1")
                .supplierId(1L)
                .supplierName("Supplier")
                .warehouseId(2L)
                .createdBy("u1")
                .build();
        PurchaseOrderLine line1 = PurchaseOrderLine.builder()
                .productId(10L)
                .productName("Widget")
                .productSku("SKU-1")
                .orderedQty(3)
                .receivedQty(3)
                .unitPrice(BigDecimal.TEN)
                .build();
        PurchaseOrderLine line2 = PurchaseOrderLine.builder()
                .productId(11L)
                .productName("Gadget")
                .productSku("SKU-2")
                .orderedQty(5)
                .receivedQty(2)
                .unitPrice(BigDecimal.ONE)
                .build();

        po.prePersist();
        line1.recalculateLineTotal();
        line2.recalculateLineTotal();
        po.addLine(line1);
        po.addLine(line2);
        po.preUpdate();

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(po.getCreatedAt()).isNotNull();
        assertThat(po.getUpdatedAt()).isNotNull();
        assertThat(po.getTotalAmount()).isEqualByComparingTo("35");
        assertThat(po.isFullyReceived()).isFalse();
        assertThat(po.isPartiallyReceived()).isTrue();
    }
}
