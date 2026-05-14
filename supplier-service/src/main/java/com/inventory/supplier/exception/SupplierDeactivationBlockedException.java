package com.inventory.supplier.exception;

public class SupplierDeactivationBlockedException extends RuntimeException {
    public SupplierDeactivationBlockedException(String message) {
        super(message);
    }
}
