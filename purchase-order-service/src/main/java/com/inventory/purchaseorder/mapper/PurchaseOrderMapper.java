package com.inventory.purchaseorder.mapper;

import com.inventory.purchaseorder.dto.response.PurchaseOrderLineResponse;
import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.entity.PurchaseOrderLine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseOrderMapper {

    public PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }

        return new PurchaseOrderResponse(
                purchaseOrder.getId(),
                purchaseOrder.getOrderNumber(),
                purchaseOrder.getSupplierId(),
                purchaseOrder.getSupplierName(),
                purchaseOrder.getWarehouseId(),
                purchaseOrder.getStatus(),
                purchaseOrder.getTotalAmount(),
                purchaseOrder.getNotes(),
                purchaseOrder.getCreatedBy(),
                purchaseOrder.getApprovedBy(),
                purchaseOrder.getCancelledBy(),
                purchaseOrder.getCancelReason(),
                purchaseOrder.getExpectedDate(),
                purchaseOrder.getReceivedAt(),
                purchaseOrder.getCreatedAt(),
                purchaseOrder.getUpdatedAt(),
                toLineResponses(purchaseOrder.getLines())
        );
    }

    public PurchaseOrderLineResponse toLineResponse(PurchaseOrderLine line) {
        if (line == null) {
            return null;
        }

        return new PurchaseOrderLineResponse(
                line.getId(),
                line.getProductId(),
                line.getProductName(),
                line.getProductSku(),
                line.getOrderedQty(),
                line.getReceivedQty(),
                line.getRemainingQty(),
                line.getUnitPrice(),
                line.getLineTotal(),
                line.isFullyReceived()
        );
    }

    private List<PurchaseOrderLineResponse> toLineResponses(List<PurchaseOrderLine> lines) {
        if (lines == null) {
            return List.of();
        }

        return lines.stream()
                .map(this::toLineResponse)
                .toList();
    }
}
