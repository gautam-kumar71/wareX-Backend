package com.inventory.supplier.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateSupplierRequest(

        @NotBlank(message = "Supplier name is required")
        @Size(max = 255)
        String name,

        @Size(max = 100)
        String contactPerson,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Contact email must be valid")
        @Size(max = 255)
        String contactEmail,

        @NotBlank(message = "Contact phone is required")
        @Size(max = 20)
        String contactPhone,

        @NotBlank(message = "Address is required")
        @Size(max = 500)
        String address,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Country is required")
        @Size(max = 100)
        String country,

        @Size(max = 20, message = "GSTIN must not exceed 20 characters")
        String gstin,

        @Min(value = 0, message = "Payment terms cannot be negative")
        @Max(value = 365, message = "Payment terms cannot exceed 365 days")
        Integer paymentTerms,

        @DecimalMin(value = "0.0", message = "Credit limit cannot be negative")
        BigDecimal creditLimit,

        String notes,

        @NotBlank(message = "Category is required")
        @Size(max = 50)
        String category
) {}
