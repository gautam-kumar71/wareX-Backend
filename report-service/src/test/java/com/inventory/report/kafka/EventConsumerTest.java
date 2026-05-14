package com.inventory.report.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.report.entity.ReportData;
import com.inventory.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private ReportRepository reportRepository;

    @Test
    void consumeStockEvent_updatesDashboardSnapshot() {
        when(reportRepository.findTopByReportNameOrderByGeneratedAtDesc("DASHBOARD")).thenReturn(Optional.empty());

        eventConsumer().consumeStockEvent("""
                {"movementType":"INBOUND","quantityDelta":3}
                """);

        ArgumentCaptor<ReportData> captor = ArgumentCaptor.forClass(ReportData.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReportName()).isEqualTo("DASHBOARD");
        assertThat(captor.getValue().getDataJson()).contains("\"totalValue\":245150.0");
    }

    @Test
    void consumeOrderEvent_updatesDashboardOnlyForApprovedOrders() {
        when(reportRepository.findTopByReportNameOrderByGeneratedAtDesc("DASHBOARD")).thenReturn(Optional.of(new ReportData()));

        eventConsumer().consumeOrderEvent("""
                {"eventType":"ORDER_APPROVED","totalAmount":1000}
                """);

        verify(reportRepository).save(any(ReportData.class));
    }

    @Test
    void consumeOrderEvent_ignoresNonApprovedOrders() {
        eventConsumer().consumeOrderEvent("""
                {"eventType":"ORDER_REJECTED","totalAmount":1000}
                """);

        verify(reportRepository, never()).save(any(ReportData.class));
    }

    @Test
    void consumer_ignoresUnreadablePayload() {
        eventConsumer().consumeStockEvent("nope");
        eventConsumer().consumeOrderEvent("nope");

        verify(reportRepository, never()).save(any(ReportData.class));
    }

    private EventConsumer eventConsumer() {
        return new EventConsumer(reportRepository, new ObjectMapper());
    }
}
