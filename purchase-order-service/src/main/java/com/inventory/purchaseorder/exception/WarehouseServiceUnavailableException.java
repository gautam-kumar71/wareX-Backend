package com.inventory.purchaseorder.exception;

public class WarehouseServiceUnavailableException extends RuntimeException {
    public WarehouseServiceUnavailableException(String message) {
        super(message);
    }
}