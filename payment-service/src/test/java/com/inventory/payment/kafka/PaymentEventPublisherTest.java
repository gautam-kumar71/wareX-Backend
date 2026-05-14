package com.inventory.payment.kafka;

import com.inventory.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishEvent_populatesMissingMetadataAndSendsToKafka() {
        PaymentEventPublisher publisher = new PaymentEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "paymentEventsTopic", "payment-events-test");

        when(kafkaTemplate.send(org.mockito.Mockito.eq("payment-events-test"), org.mockito.Mockito.eq("TXN-1"), org.mockito.Mockito.any(PaymentEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_PROCESSED")
                .transactionId("TXN-1")
                .invoiceNumber("INV-1")
                .amount(BigDecimal.TEN)
                .status(PaymentStatus.COMPLETED)
                .triggeredBy("alice")
                .build();

        publisher.publishEvent(event);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOccurredAt()).isNotNull();

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaTemplate).send(org.mockito.Mockito.eq("payment-events-test"), org.mockito.Mockito.eq("TXN-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getInvoiceNumber()).isEqualTo("INV-1");
    }

    @Test
    void publishEvent_preservesExistingMetadata() {
        PaymentEventPublisher publisher = new PaymentEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "paymentEventsTopic", "payment-events-test");

        when(kafkaTemplate.send(org.mockito.Mockito.eq("payment-events-test"), org.mockito.Mockito.eq("TXN-2"), org.mockito.Mockito.any(PaymentEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
        PaymentEvent event = PaymentEvent.builder()
                .eventId("evt-1")
                .eventType("PAYMENT_PROCESSED")
                .transactionId("TXN-2")
                .invoiceNumber("INV-2")
                .amount(BigDecimal.ONE)
                .status(PaymentStatus.COMPLETED)
                .triggeredBy("bob")
                .occurredAt(occurredAt)
                .build();

        publisher.publishEvent(event);

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
    }
}
