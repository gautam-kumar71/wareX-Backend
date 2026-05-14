package com.inventory.purchaseorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PurchaseOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseOrderServiceApplication.class, args);
    }
}