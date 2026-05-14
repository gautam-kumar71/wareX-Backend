package com.inventory.stockmovement.kafka;

import com.inventory.stockmovement.entity.StockMovement;
import com.inventory.stockmovement.repository.StockMovementRepository;
import com.inventory.stockmovement.service.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockEventConsumerTest {

    @Mock
    private StockMovementRepository movementRepo;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private StockEventConsumer consumer;

    private StockEventPayload payload;
    private ConsumerRecord<String, StockEventPayload> record;

    @BeforeEach
    void setUp() {
        String eventId = UUID.randomUUID().toString();
        payload = StockEventPayload.builder()
                .eventId(eventId)
                .movementType("RECEIPT")
                .productId(100L)
                .warehouseId(200L)
                .quantityDelta(50)
                .newQuantity(150)
                .occurredAt(Instant.now())
                .build();

        record = new ConsumerRecord<>("stock-events", 0, 0L, eventId, payload);
    }

    @Test
    void shouldProcessSuccessfullyWhenNewEvent() {
        when(idempotencyService.isAlreadyProcessed(payload.getEventId())).thenReturn(false);

        consumer.consume(record, acknowledgment);

        verify(movementRepo).save(any(StockMovement.class));
        verify(idempotencyService).markProcessed(payload.getEventId());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldSkipProcessingWhenEventAlreadyInRedis() {
        when(idempotencyService.isAlreadyProcessed(payload.getEventId())).thenReturn(true);

        consumer.consume(record, acknowledgment);

        verify(movementRepo, never()).save(any(StockMovement.class));
        verify(idempotencyService, never()).markProcessed(anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldAcknowledgeWhenDbConstraintFails() {
        when(idempotencyService.isAlreadyProcessed(payload.getEventId())).thenReturn(false);
        when(movementRepo.save(any(StockMovement.class))).thenThrow(new DataIntegrityViolationException("Duplicate"));

        consumer.consume(record, acknowledgment);

        verify(movementRepo).save(any(StockMovement.class));
        verify(idempotencyService).markProcessed(payload.getEventId());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldAcknowledgeNullEvent() {
        consumer.consume(new ConsumerRecord<>("stock-events", 0, 0L, "key", null), acknowledgment);

        verify(movementRepo, never()).save(any());
        verify(acknowledgment).acknowledge();
    }
}
