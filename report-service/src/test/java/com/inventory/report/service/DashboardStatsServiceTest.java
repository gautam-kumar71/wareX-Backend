package com.inventory.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.report.dto.response.DashboardStats;
import com.inventory.report.entity.ReportData;
import com.inventory.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardStatsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ReportRepository reportRepository;

    @Test
    void loadDashboardStats_prefersLiveDatabaseValues() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse_db.warehouses WHERE active = true", Long.class))
                .thenReturn(4L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supplier_db.suppliers WHERE active = true", Long.class))
                .thenReturn(9L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_movement_db.stock_movements", Long.class))
                .thenReturn(12L);
        when(jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM purchase_order_db.purchase_orders
                            WHERE status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED')
                            """, Long.class))
                .thenReturn(6L);
        when(jdbcTemplate.queryForObject("""
                            SELECT COALESCE(SUM(total_amount), 0)
                            FROM purchase_order_db.purchase_orders
                            WHERE status <> 'CANCELLED'
                            """, Number.class))
                .thenReturn(245310.0);

        DashboardStats stats = dashboardStatsService().loadDashboardStats();

        assertThat(stats.getTotalWarehouses()).isEqualTo(4);
        assertThat(stats.getActiveSuppliers()).isEqualTo(9);
        assertThat(stats.getShipments()).isEqualTo(12);
        assertThat(stats.getOpenOrders()).isEqualTo(6);
        assertThat(stats.getTotalValue()).isEqualTo(245310.0);

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).queryForObject("SELECT COUNT(*) FROM warehouse_db.warehouses WHERE active = true", Long.class);
        inOrder.verify(jdbcTemplate).queryForObject("SELECT COUNT(*) FROM supplier_db.suppliers WHERE active = true", Long.class);
        inOrder.verify(jdbcTemplate).queryForObject("SELECT COUNT(*) FROM stock_movement_db.stock_movements", Long.class);
    }

    @Test
    void loadDashboardStats_fallsBackToPersistedSnapshotWhenLiveAggregationFails() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse_db.warehouses WHERE active = true", Long.class))
                .thenThrow(new RuntimeException("db unavailable"));
        when(reportRepository.findTopByReportNameOrderByGeneratedAtDesc("DASHBOARD"))
                .thenReturn(Optional.of(ReportData.builder()
                        .reportName("DASHBOARD")
                        .dataJson("{\"totalWarehouses\":7,\"activeSuppliers\":14,\"shipments\":22,\"openOrders\":5,\"totalValue\":9000.5}")
                        .build()));

        DashboardStats stats = dashboardStatsService().loadDashboardStats();

        assertThat(stats.getTotalWarehouses()).isEqualTo(7);
        assertThat(stats.getActiveSuppliers()).isEqualTo(14);
        assertThat(stats.getShipments()).isEqualTo(22);
        assertThat(stats.getOpenOrders()).isEqualTo(5);
        assertThat(stats.getTotalValue()).isEqualTo(9000.5);
    }

    private DashboardStatsService dashboardStatsService() {
        return new DashboardStatsService(jdbcTemplate, reportRepository, new ObjectMapper());
    }
}
