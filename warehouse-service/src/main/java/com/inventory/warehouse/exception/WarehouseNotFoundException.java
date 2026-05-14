package com.inventory.warehouse.exception;

public class WarehouseNotFoundException extends RuntimeException {
    public WarehouseNotFoundException(Long id) {
        super("Warehouse not found with id: " + id);
    }
    public WarehouseNotFoundException(String message) {
        super(message);
    }
}