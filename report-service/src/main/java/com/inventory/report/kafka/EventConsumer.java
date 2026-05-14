package com.inventory.report.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.report.entity.ReportData;
import com.inventory.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    // Simple in-memory aggregation for demonstration.
    // In a production app, this would query read-models or use Kafka Streams.
    private long totalWarehouses = 5;
    private long activeSuppliers = 12;
    private double totalValue = 245000.0;

    @KafkaListener(topics = "stock-events", groupId = "report-service-group")
    public void consumeStockEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("movementType").asText();
            double deltaValue = event.path("quantityDelta").asDouble() * 50.0; // Mock unit price

            totalValue += deltaValue;
            updateDashboard();
            log.info("Report updated with stock event. New Total Value: {}", totalValue);
        } catch (Exception e) {
            log.error("Failed to process stock event in report-service", e);
        }
    }

    @KafkaListener(topics = "order-events", groupId = "report-service-group")
    public void consumeOrderEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("eventType").asText();

            if ("ORDER_APPROVED".equals(type)) {
                double orderTotal = event.path("totalAmount").asDouble();
                totalValue += orderTotal;
                updateDashboard();
                log.info("Report updated with order approval. New Total Value: {}", totalValue);
            }
        } catch (Exception e) {
            log.error("Failed to process order event in report-service", e);
        }
    }

    private void updateDashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWarehouses", totalWarehouses);
        stats.put("activeSuppliers", activeSuppliers);
        stats.put("totalValue", totalValue);

        try {
            String json = objectMapper.writeValueAsString(stats);
            ReportData report = reportRepository.findTopByReportNameOrderByGeneratedAtDesc("DASHBOARD")
                    .orElse(new ReportData());
            
            report.setReportName("DASHBOARD");
            report.setDataJson(json);
            reportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to save dashboard report", e);
        }
    }
}
