package com.inventory.purchaseorder.exception;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(PurchaseOrderStatus from,
                                            PurchaseOrderStatus to) {
        super("Cannot transition purchase order from %s to %s"
                .formatted(from, to));
    }
}