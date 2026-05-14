package com.inventory.warehouse.feign;

import com.inventory.warehouse.config.FeignSecurityConfig;
import com.inventory.warehouse.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "product-service",
        url = "${app.services.product-service.url}",
        configuration = FeignSecurityConfig.class
)
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    ApiResponse<ProductResponse> getProductById(@PathVariable("id") Long id);

    record ProductResponse(Long id, String name, String sku) {}
}
