package com.inventory.stockmovement;

import com.inventory.stockmovement.feign.ProductClient;
import com.inventory.stockmovement.feign.PurchaseOrderClient;
import com.inventory.stockmovement.feign.WarehouseClient;
import com.inventory.stockmovement.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = StockMovementServiceApplication.class,
        properties = {
                "spring.main.lazy-initialization=true",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@ActiveProfiles("test")
class StockMovementServiceApplicationTests {

    @MockBean StockMovementRepository stockMovementRepository;
    @MockBean ProductClient productClient;
    @MockBean PurchaseOrderClient purchaseOrderClient;
    @MockBean WarehouseClient warehouseClient;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void contextLoads() {
    }

}
