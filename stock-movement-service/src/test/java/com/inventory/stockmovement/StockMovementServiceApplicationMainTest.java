package com.inventory.stockmovement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class StockMovementServiceApplicationMainTest {

    @Test
    void main_delegatesToSpringApplication() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            StockMovementServiceApplication.main(new String[]{"--test"});

            springApplication.verify(() ->
                    SpringApplication.run(StockMovementServiceApplication.class, new String[]{"--test"}));
        }
    }
}
