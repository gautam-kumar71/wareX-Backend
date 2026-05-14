package com.inventory.warehouse;

import com.inventory.warehouse.feign.ProductClient;
import com.inventory.warehouse.repository.StockLevelRepository;
import com.inventory.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
class WarehouseServiceApplicationTests {

    @MockBean StockLevelRepository stockLevelRepository;
    @MockBean WarehouseRepository warehouseRepository;
    @MockBean ProductClient productClient;

    @Test
    void contextLoads() {
    }

}
