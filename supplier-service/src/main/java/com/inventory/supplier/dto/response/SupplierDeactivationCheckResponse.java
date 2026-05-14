package com.inventory.supplier.dto.response;

import java.util.List;

public record SupplierDeactivationCheckResponse(
        boolean canDeactivate,
        long blockingOrderCount,
        List<String> blockingStatuses,
        List<String> blockingOrderNumbers,
        long blockingInvoiceCount,
        List<String> blockingInvoiceStatuses,
        List<String> blockingInvoiceNumbers,
        String message
) {}
