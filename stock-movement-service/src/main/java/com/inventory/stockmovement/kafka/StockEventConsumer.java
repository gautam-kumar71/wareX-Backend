package com.inventory.stockmovement.kafka;

import com.inventory.stockmovement.entity.StockMovement;
import com.inventory.stockmovement.enums.MovementType;
import com.inventory.stockmovement.repository.StockMovementRepository;
import com.inventory.stockmovement.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent Kafka consumer for stock-events.
 *
 * Idempotency strategy (two-layer):
 *   Layer 1 — Redis pre-check: fast, cheap. If eventId already in Redis → skip.
 *   Layer 2 — DB unique constraint on event_id: catches the rare case where
 *              Redis TTL expired but the row still exists.
 *
 * Retry strategy:
 *   - 3 retry attempts with exponential backoff (1s, 2s, 4s)
 *   - After 3 failures → published to stock-events.DLT (Dead Letter Topic)
 *   - DLT messages are logged for manual inspection / replay
 *
 * Consumer group: stock-movement-service
 * Offset commit: manual (AFTER successful DB insert)
 * Isolation level: read_committed (ignores uncommitted messages from producers)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventConsumer {

    private final StockMovementRepository movementRepo;
    private final IdempotencyService      idempotencyService;
    @RetryableTopic(
            attempts = "4",          // 1 original + 3 retries
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(
            topics = "${kafka.topics.stock-events:stock-events}",
            groupId = "stock-movement-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, StockEventPayload> record,
                        Acknowledgment ack) {

        StockEventPayload event = record.value();

        if (event == null || event.getEventId() == null) {
            log.warn("Received null or malformed stock event — skipping. offset={}",
                    record.offset());
            ack.acknowledge();
            return;
        }

        log.debug("Consuming stock event: eventId={}, type={}, product={}, warehouse={}",
                event.getEventId(), event.getMovementType(),
                event.getProductId(), event.getWarehouseId());

        // ── Layer 1: Redis idempotency check (fast path) ──────────────────
        if (idempotencyService.isAlreadyProcessed(event.getEventId())) {
            log.debug("Duplicate event skipped (Redis): eventId={}", event.getEventId());
            ack.acknowledge();
            return;
        }

        try {
            // ── Map and insert ─────────────────────────────────────────────
            MovementType movementType = parseMovementType(event.getMovementType());

            StockMovement movement = StockMovement.builder()
                    .eventId(event.getEventId())
                    .productId(event.getProductId())
                    .productName(resolveProductName(event))
                    .warehouseId(event.getWarehouseId())
                    .warehouseName(resolveWarehouseName(event))
                    .movementType(movementType)
                    .quantityDelta(event.getQuantityDelta())
                    .quantityAfter(event.getNewQuantity())
                    .referenceId(event.getReferenceId())
                    .referenceType(event.getReferenceType())
                    .occurredAt(event.getOccurredAt() != null
                            ? event.getOccurredAt()
                            : java.time.Instant.now())
                    .build();

            movementRepo.save(movement);

            // ── Mark as processed in Redis (24h TTL) ───────────────────────
            idempotencyService.markProcessed(event.getEventId());

            ack.acknowledge();

            log.info("Stock movement recorded: eventId={}, type={}, product={}, warehouse={}, delta={}",
                    event.getEventId(), movementType,
                    event.getProductId(), event.getWarehouseId(), event.getQuantityDelta());

        } catch (DataIntegrityViolationException ex) {
            // ── Layer 2: DB unique constraint fired — true duplicate ────────
            // This can only happen if Redis TTL expired but the row persists.
            // Safe to ack and move on.
            log.warn("Duplicate event dropped (DB constraint): eventId={}", event.getEventId());
            idempotencyService.markProcessed(event.getEventId()); // refresh Redis TTL
            ack.acknowledge();
        }
    }

    /**
     * Dead Letter Topic consumer — called after all retry attempts exhausted.
     * We log with ERROR so it triggers alerts in production, then acknowledge
     * so the DLT offset advances (preventing infinite reprocessing).
     */
    @KafkaListener(
            topics = "${kafka.topics.stock-events-dlt:stock-events.DLT}",
            groupId = "stock-movement-service-dlt"
    )
    public void consumeDlt(ConsumerRecord<String, StockEventPayload> record,
                           Acknowledgment ack) {
        StockEventPayload event = record.value();
        log.error("DEAD LETTER: stock event could not be processed after all retries. " +
                        "eventId={}, type={}, product={}, warehouse={}, offset={}. " +
                        "Manual intervention required.",
                event != null ? event.getEventId() : "null",
                event != null ? event.getMovementType() : "null",
                event != null ? event.getProductId() : "null",
                event != null ? event.getWarehouseId() : "null",
                record.offset());
        ack.acknowledge();
    }

    private MovementType parseMovementType(String type) {
        if (type == null) return MovementType.ADJUSTMENT_ADD;
        try {
            return MovementType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown movement type '{}' — defaulting to ADJUSTMENT_ADD", type);
            return MovementType.ADJUSTMENT_ADD;
        }
    }

    private String resolveProductName(StockEventPayload event) {
        if (event.getProductName() != null && !event.getProductName().isBlank()) {
            return event.getProductName();
        }
        return "Product #" + event.getProductId();
    }

    private String resolveWarehouseName(StockEventPayload event) {
        if (event.getWarehouseName() != null && !event.getWarehouseName().isBlank()) {
            return event.getWarehouseName();
        }
        return "Warehouse #" + event.getWarehouseId();
    }
}
