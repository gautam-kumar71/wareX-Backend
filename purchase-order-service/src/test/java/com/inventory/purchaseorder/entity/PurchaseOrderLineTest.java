package com.inventory.purchaseorder.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderLineTest {

    @Test
    void lifecycleAndHelperMethods_work() {
        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .productId(10L)
                .productName("Widget")
                .productSku("SKU-1")
                .orderedQty(4)
                .receivedQty(1)
                .unitPrice(new BigDecimal("12.50"))
                .build();

        line.prePersist();
        line.preUpdate();

        assertThat(line.getCreatedAt()).isNotNull();
        assertThat(line.getUpdatedAt()).isNotNull();
        assertThat(line.getLineTotal()).isEqualByComparingTo("50.00");
        assertThat(line.isFullyReceived()).isFalse();
        assertThat(line.getRemainingQty()).isEqualTo(3);
    }
}
