package com.inventory.product;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class ProductServiceApplicationMainTest {

    @Test
    void main_delegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            ProductServiceApplication.main(new String[]{"--spring.main.banner-mode=off"});

            spring.verify(() -> SpringApplication.run(ProductServiceApplication.class, new String[]{"--spring.main.banner-mode=off"}));
        }
    }
}
