package com.inventory.stockmovement.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.inventory.stockmovement.dto.response.ApiResponse;

@FeignClient(name = "warehouse-service", url = "${app.services.warehouse-service.url}")
public interface WarehouseClient {

    @GetMapping("/api/v1/warehouses/{id}")
    ApiResponse<WarehouseResponse> getWarehouseById(@PathVariable("id") Long id);

    record WarehouseResponse(Long id, String name, String city, boolean active) {}
}
