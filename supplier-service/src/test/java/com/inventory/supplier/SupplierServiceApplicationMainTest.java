package com.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class SupplierServiceApplicationMainTest {

    @Test
    void main_delegatesToSpringApplication() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            SupplierServiceApplication.main(new String[]{"--test"});

            springApplication.verify(() -> SpringApplication.run(SupplierServiceApplication.class, new String[]{"--test"}));
        }
    }
}
