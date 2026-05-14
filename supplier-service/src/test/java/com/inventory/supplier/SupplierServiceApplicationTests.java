package com.inventory.supplier;

import com.inventory.supplier.feign.PurchaseOrderClient;
import com.inventory.supplier.mapper.SupplierMapper;
import com.inventory.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration," +
                "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@ActiveProfiles("test")
class SupplierServiceApplicationTests {

    @MockBean
    SupplierRepository supplierRepository;

    @MockBean
    SupplierMapper supplierMapper;

    @MockBean
    PurchaseOrderClient purchaseOrderClient;

    @MockBean
    JavaMailSender javaMailSender;

    @Test
    void contextLoads() {
    }

}
