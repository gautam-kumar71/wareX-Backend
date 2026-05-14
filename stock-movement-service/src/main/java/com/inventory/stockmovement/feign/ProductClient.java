package com.inventory.stockmovement.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.inventory.stockmovement.dto.response.ApiResponse;

@FeignClient(name = "product-service", url = "${app.services.product-service.url}")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    ApiResponse<ProductResponse> getProductById(@PathVariable("id") Long id);

    record ProductResponse(Long id, String name, String sku) {}
}
