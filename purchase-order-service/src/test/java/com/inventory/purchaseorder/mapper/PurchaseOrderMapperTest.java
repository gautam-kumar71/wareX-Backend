package com.inventory.purchaseorder.mapper;

import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.entity.PurchaseOrderLine;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderMapperTest {

    private final PurchaseOrderMapper mapper = new PurchaseOrderMapper();

    @Test
    void toResponse_mapsOrderAndLines() {
        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .id(1L)
                .productId(10L)
                .productName("Widget")
                .productSku("SKU-1")
                .orderedQty(5)
                .receivedQty(2)
                .unitPrice(BigDecimal.TEN)
                .lineTotal(new BigDecimal("50"))
                .build();
        PurchaseOrder order = PurchaseOrder.builder()
                .id(9L)
                .orderNumber("PO-9")
                .supplierId(5L)
                .supplierName("Supplier")
                .warehouseId(7L)
                .status(PurchaseOrderStatus.APPROVED)
                .totalAmount(new BigDecimal("50"))
                .notes("notes")
                .createdBy("u1")
                .expectedDate(LocalDate.parse("2026-01-10"))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .lines(List.of(line))
                .build();

        PurchaseOrderResponse response = mapper.toResponse(order);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.orderNumber()).isEqualTo("PO-9");
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).remainingQty()).isEqualTo(3);
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toLineResponse(null)).isNull();
    }
}
