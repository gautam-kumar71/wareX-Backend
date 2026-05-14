package com.inventory.purchaseorder;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class PurchaseOrderServiceApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            PurchaseOrderServiceApplication.main(new String[]{"--test"});
            mocked.verify(() -> SpringApplication.run(PurchaseOrderServiceApplication.class, new String[]{"--test"}));
        }
    }
}
