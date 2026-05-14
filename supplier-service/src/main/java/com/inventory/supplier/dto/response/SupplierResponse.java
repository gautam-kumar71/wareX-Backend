package com.inventory.supplier.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SupplierResponse(
        Long id,
        String name,
        String contactPerson,
        String contactEmail,
        String contactPhone,
        String address,
        String city,
        String country,
        String gstin,
        int paymentTerms,
        BigDecimal creditLimit,
        String notes,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}