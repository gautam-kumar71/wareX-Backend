package com.inventory.purchaseorder.feign.fallback;

import com.inventory.purchaseorder.feign.WarehouseClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WarehouseClientFallback implements WarehouseClient {

    @Override
    public ApiResponse<WarehouseResponse> getWarehouseById(Long id) {
        log.error("Warehouse Service unavailable — circuit open. warehouseId={}", id);
        // Return null so the service layer can detect the failure and throw a meaningful error
        return null;
    }

    @Override
    public void receiveStock(Long warehouseId, Long productId,
                             int quantity, String purchaseOrderId) {
        log.error("Warehouse Service unavailable — stock receipt failed. " +
                        "warehouseId={}, productId={}, qty={}, PO={}",
                warehouseId, productId, quantity, purchaseOrderId);
        throw new com.inventory.purchaseorder.exception.WarehouseServiceUnavailableException(
                "Warehouse Service is currently unavailable. Please retry the stock receipt.");
    }

    @Override
    public void reverseReceivedStock(Long warehouseId, Long productId,
                                     int quantity, String purchaseOrderId) {
        log.error("Warehouse Service unavailable — stock reversal failed. " +
                        "warehouseId={}, productId={}, qty={}, PO={}",
                warehouseId, productId, quantity, purchaseOrderId);
        throw new com.inventory.purchaseorder.exception.WarehouseServiceUnavailableException(
                "Warehouse Service is currently unavailable. Please retry the stock reversal.");
    }
}
