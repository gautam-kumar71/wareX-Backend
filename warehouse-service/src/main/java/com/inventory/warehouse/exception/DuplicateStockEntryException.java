package com.inventory.warehouse.exception;

public class DuplicateStockEntryException extends RuntimeException {
    public DuplicateStockEntryException(Long warehouseId, Long productId) {
        super("Stock level already exists for product %d in warehouse %d"
                .formatted(productId, warehouseId));
    }
}