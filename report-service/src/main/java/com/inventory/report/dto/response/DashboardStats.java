package com.inventory.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long totalWarehouses;
    private long activeSuppliers;
    private long shipments;
    private long openOrders;
    private double totalValue;
}
