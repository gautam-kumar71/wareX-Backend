package com.inventory.purchaseorder.feign.fallback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierClientFallbackTest {

    @Test
    void getSupplierById_returnsNullFallback() {
        assertThat(new SupplierClientFallback().getSupplierById(1L)).isNull();
    }
}
