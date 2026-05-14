package com.inventory.alert.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.alert.entity.Alert;
import com.inventory.alert.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private AlertRepository alertRepository;

    @Test
    void consumePaymentEvent_createsAlertForFailedPayment() {
        when(alertRepository.existsByEventId("evt-1")).thenReturn(false);

        eventConsumer().consumePaymentEvent("""
                {"eventId":"evt-1","status":"FAILED","transactionId":"TX-9"}
                """);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Payment Failed");
        assertThat(captor.getValue().getMessage()).contains("TX-9");
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void consumePaymentEvent_skipsDuplicateEvent() {
        when(alertRepository.existsByEventId("evt-2")).thenReturn(true);

        eventConsumer().consumePaymentEvent("""
                {"eventId":"evt-2","status":"FAILED","transactionId":"TX-10"}
                """);

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void consumeStockEvent_createsLowStockAlertUsingFallbackQuantityField() {
        when(alertRepository.existsByEventId("evt-3")).thenReturn(false);

        eventConsumer().consumeStockEvent("""
                {"eventId":"evt-3","productId":42,"productName":"Widget","warehouseName":"Main","quantityAfter":3,"lowStock":true}
                """);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("LOW_STOCK");
        assertThat(captor.getValue().getMessage()).contains("Widget", "Main", "3");
    }

    @Test
    void consumeStockEvent_createsOverstockAlertWithProductFallbackName() {
        when(alertRepository.existsByEventId("evt-4")).thenReturn(false);

        eventConsumer().consumeStockEvent("""
                {"eventId":"evt-4","productId":99,"newQuantity":250,"overstock":true}
                """);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("OVERSTOCK");
        assertThat(captor.getValue().getMessage()).contains("Product #99", "250");
    }

    @Test
    void consumeStockEvent_ignoresInvalidPayload() {
        eventConsumer().consumeStockEvent("not-json");

        verify(alertRepository, never()).save(any(Alert.class));
    }

    private EventConsumer eventConsumer() {
        return new EventConsumer(alertRepository, new ObjectMapper());
    }
}
