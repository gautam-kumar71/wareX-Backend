package com.inventory.warehouse.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, Long warehouseId,
                                      int requested, int available) {
        super("Insufficient stock for product %d in warehouse %d. Requested: %d, Available: %d"
                .formatted(productId, warehouseId, requested, available));
    }
    public InsufficientStockException(String message) {
        super(message);
    }
}