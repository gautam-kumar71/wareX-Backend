package com.inventory.report.controller;

import com.inventory.report.dto.response.DashboardStats;
import com.inventory.report.service.DashboardStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private DashboardStatsService dashboardStatsService;

    @Test
    void getDashboardStats_readsServicePayload() {
        when(dashboardStatsService.loadDashboardStats())
                .thenReturn(DashboardStats.builder()
                        .totalWarehouses(7)
                        .activeSuppliers(14)
                        .shipments(22)
                        .openOrders(5)
                        .totalValue(9000.5)
                        .build());

        DashboardStats stats = reportController().getDashboardStats().getBody().data();

        assertThat(stats.getTotalWarehouses()).isEqualTo(7);
        assertThat(stats.getActiveSuppliers()).isEqualTo(14);
        assertThat(stats.getShipments()).isEqualTo(22);
        assertThat(stats.getOpenOrders()).isEqualTo(5);
        assertThat(stats.getTotalValue()).isEqualTo(9000.5);
    }

    @Test
    void downloadStockMovementSummaryPdf_returnsPdfAttachment() {
        when(dashboardStatsService.loadDashboardStats()).thenReturn(DashboardStats.builder()
                .totalWarehouses(5)
                .activeSuppliers(12)
                .shipments(18)
                .openOrders(4)
                .totalValue(245000.0)
                .build());

        byte[] body = reportController().downloadStockMovementSummaryPdf().getBody();

        assertThat(reportController().downloadStockMovementSummaryPdf().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(body, StandardCharsets.US_ASCII)).contains("%PDF-1.4");
    }

    @Test
    void downloadSupplierPerformanceExcel_returnsSpreadsheetPayload() {
        when(dashboardStatsService.loadDashboardStats()).thenReturn(DashboardStats.builder()
                .totalWarehouses(5)
                .activeSuppliers(12)
                .shipments(18)
                .openOrders(4)
                .totalValue(245000.0)
                .build());

        var response = reportController().downloadSupplierPerformanceExcel();

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/vnd.ms-excel");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("Active Suppliers");
    }

    @Test
    void downloadFinancialReconciliationCsv_returnsCsvAttachment() {
        when(dashboardStatsService.loadDashboardStats()).thenReturn(DashboardStats.builder()
                .totalWarehouses(5)
                .activeSuppliers(12)
                .shipments(18)
                .openOrders(4)
                .totalValue(245000.0)
                .build());

        var response = reportController().downloadFinancialReconciliationCsv();

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                .contains("metric,value")
                .contains("shipments,18")
                .contains("open_orders,4");
    }

    private ReportController reportController() {
        return new ReportController(dashboardStatsService);
    }
}
