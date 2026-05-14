package com.inventory.warehouse.exception;

public class StockLevelNotFoundException extends RuntimeException {
    public StockLevelNotFoundException(Long warehouseId, Long productId) {
        super("No stock record found for product %d in warehouse %d"
                .formatted(productId, warehouseId));
    }
    public StockLevelNotFoundException(String message) {
        super(message);
    }
}