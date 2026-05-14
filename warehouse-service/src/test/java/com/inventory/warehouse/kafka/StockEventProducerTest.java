package com.inventory.warehouse.kafka;

import com.inventory.warehouse.enums.MovementType;
import com.inventory.warehouse.event.StockEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockEventProducerTest {

    @Mock
    private KafkaTemplate<String, StockEvent> kafkaTemplate;

    @InjectMocks
    private StockEventProducer producer;

    @Test
    void publishStockEvent_sendsToKafka() {
        StockEvent event = event();
        SendResult<String, StockEvent> sendResult = new SendResult<>(
                new ProducerRecord<>("stock-events", "11", event),
                new RecordMetadata(new TopicPartition("stock-events", 0), 0L, 5, 0L, 0L, 0, 0)
        );
        given(kafkaTemplate.send(eq("stock-events"), eq("11"), eq(event)))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishStockEvent(event);

        verify(kafkaTemplate).send("stock-events", "11", event);
    }

    @Test
    void publishStockEvent_swallowSynchronousKafkaFailure() {
        StockEvent event = event();
        given(kafkaTemplate.send(any(), any(), any())).willThrow(new RuntimeException("broker down"));

        producer.publishStockEvent(event);

        verify(kafkaTemplate).send("stock-events", "11", event);
    }

    private StockEvent event() {
        return StockEvent.builder()
                .eventId("evt-1")
                .movementType(MovementType.ADJUSTMENT_ADD)
                .productId(11L)
                .warehouseId(4L)
                .newQuantity(50)
                .availableQuantity(50)
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
