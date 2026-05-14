package com.inventory.purchaseorder.feign.fallback;

import com.inventory.purchaseorder.exception.WarehouseServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseClientFallbackTest {

    @Test
    void getWarehouseById_returnsNullFallback() {
        assertThat(new WarehouseClientFallback().getWarehouseById(1L)).isNull();
    }

    @Test
    void receiveStock_throwsServiceUnavailableException() {
        assertThatThrownBy(() -> new WarehouseClientFallback().receiveStock(1L, 2L, 3, "PO-1"))
                .isInstanceOf(WarehouseServiceUnavailableException.class)
                .hasMessageContaining("Warehouse Service");
    }

    @Test
    void reverseReceivedStock_throwsServiceUnavailableException() {
        assertThatThrownBy(() -> new WarehouseClientFallback().reverseReceivedStock(1L, 2L, 3, "PO-1"))
                .isInstanceOf(WarehouseServiceUnavailableException.class)
                .hasMessageContaining("Warehouse Service");
    }
}
