package com.inventory.supplier.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateSupplierRequest(

        @Size(max = 255)
        String name,

        @Size(max = 100)
        String contactPerson,

        @Email(message = "Contact email must be valid")
        @Size(max = 255)
        String contactEmail,

        @Size(max = 20)
        String contactPhone,

        @Size(max = 500)
        String address,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String country,

        @Size(max = 20)
        String gstin,

        @Min(0) @Max(365)
        Integer paymentTerms,

        @DecimalMin("0.0")
        BigDecimal creditLimit,

        String notes,

        @Size(max = 50)
        String category,

        Boolean active
) {}