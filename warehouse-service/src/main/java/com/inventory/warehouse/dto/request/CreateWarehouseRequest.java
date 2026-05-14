package com.inventory.warehouse.dto.request;

import jakarta.validation.constraints.*;

public record CreateWarehouseRequest(

        @NotBlank(message = "Warehouse name is required")
        @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
        String name,

        @NotBlank(message = "Location/address is required")
        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @Min(value = 0, message = "Total storage capacity cannot be negative")
        Integer totalStorageCapacity,

        @Size(max = 100, message = "Manager name must not exceed 100 characters")
        String managerName,

        @Size(max = 50, message = "Contact phone must not exceed 50 characters")
        String contactPhone
) {}
