package com.inventory.purchaseorder.exception;

public class PurchaseOrderNotFoundException extends RuntimeException {
    public PurchaseOrderNotFoundException(Long id) {
        super("Purchase order not found with id: " + id);
    }
    public PurchaseOrderNotFoundException(String orderNumber) {
        super("Purchase order not found with order number: " + orderNumber);
    }
}