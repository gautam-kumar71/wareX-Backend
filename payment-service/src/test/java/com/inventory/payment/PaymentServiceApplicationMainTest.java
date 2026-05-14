package com.inventory.payment;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class PaymentServiceApplicationMainTest {

    @Test
    void main_delegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            PaymentServiceApplication.main(new String[]{"--spring.main.banner-mode=off"});

            spring.verify(() -> SpringApplication.run(PaymentServiceApplication.class, new String[]{"--spring.main.banner-mode=off"}));
        }
    }
}
