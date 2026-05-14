package com.inventory.purchaseorder.feign.fallback;

import com.inventory.purchaseorder.feign.SupplierClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SupplierClientFallback implements SupplierClient {

    @Override
    public ApiResponse<SupplierResponse> getSupplierById(Long id) {
        log.error("Supplier Service unavailable — circuit open. supplierId={}", id);
        return null;
    }
}