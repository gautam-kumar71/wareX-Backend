package com.inventory.supplier.exception;

public class SupplierDependencyUnavailableException extends RuntimeException {
    public SupplierDependencyUnavailableException(String message) {
        super(message);
    }
}
