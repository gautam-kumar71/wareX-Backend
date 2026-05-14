package com.inventory.warehouse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class WarehouseServiceApplicationMainTest {

    @Test
    void mainDelegatesToSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
            WarehouseServiceApplication.main(new String[]{"--test"});
            springApplication.verify(() -> SpringApplication.run(WarehouseServiceApplication.class, new String[]{"--test"}));
        }
    }
}
