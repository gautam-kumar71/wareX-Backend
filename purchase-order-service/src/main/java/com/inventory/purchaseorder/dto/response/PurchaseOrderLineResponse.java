package com.inventory.purchaseorder.dto.response;

import java.math.BigDecimal;

public record PurchaseOrderLineResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        int orderedQty,
        int receivedQty,
        int remainingQty,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        boolean fullyReceived
) {}