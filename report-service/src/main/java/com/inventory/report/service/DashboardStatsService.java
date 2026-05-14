package com.inventory.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.report.dto.response.DashboardStats;
import com.inventory.report.entity.ReportData;
import com.inventory.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private static final String DEFAULT_DATA = """
            {
              "totalWarehouses": 0,
              "activeSuppliers": 0,
              "shipments": 0,
              "openOrders": 0,
              "totalValue": 0
            }
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    public DashboardStats loadDashboardStats() {
        try {
            return DashboardStats.builder()
                    .totalWarehouses(queryForLong("SELECT COUNT(*) FROM warehouse_db.warehouses WHERE active = true"))
                    .activeSuppliers(queryForLong("SELECT COUNT(*) FROM supplier_db.suppliers WHERE active = true"))
                    .shipments(queryForLong("SELECT COUNT(*) FROM stock_movement_db.stock_movements"))
                    .openOrders(queryForLong("""
                            SELECT COUNT(*)
                            FROM purchase_order_db.purchase_orders
                            WHERE status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED')
                            """))
                    .totalValue(queryForDouble("""
                            SELECT COALESCE(SUM(total_amount), 0)
                            FROM purchase_order_db.purchase_orders
                            WHERE status <> 'CANCELLED'
                            """))
                    .build();
        } catch (Exception e) {
            log.warn("Falling back to persisted dashboard snapshot because live aggregation failed", e);
            return loadPersistedFallback();
        }
    }

    private DashboardStats loadPersistedFallback() {
        String data = reportRepository.findTopByReportNameOrderByGeneratedAtDesc("DASHBOARD")
                .map(ReportData::getDataJson)
                .orElse(DEFAULT_DATA);

        try {
            return objectMapper.readValue(data, DashboardStats.class);
        } catch (Exception e) {
            log.warn("Persisted dashboard snapshot could not be parsed, returning zeroed defaults", e);
            return objectMapper.convertValue(
                    Map.of(
                            "totalWarehouses", 0,
                            "activeSuppliers", 0,
                            "shipments", 0,
                            "openOrders", 0,
                            "totalValue", 0
                    ),
                    DashboardStats.class
            );
        }
    }

    private long queryForLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private double queryForDouble(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0.0 : value.doubleValue();
    }
}
