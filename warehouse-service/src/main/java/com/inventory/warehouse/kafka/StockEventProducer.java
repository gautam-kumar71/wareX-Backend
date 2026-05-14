package com.inventory.warehouse.kafka;

import com.inventory.warehouse.event.StockEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventProducer {

    // Topic consumed by: Stock Movement Service, Alert Service, Report Service
    private static final String TOPIC = "stock-events";

    private final KafkaTemplate<String, StockEvent> kafkaTemplate;

    /**
     * Publishes a StockEvent to the "stock-events" Kafka topic.
     *
     * The message key is the productId (as a string) so all events for the same
     * product land on the same partition — guaranteeing ordering per product.
     *
     * Send is asynchronous; failures are logged but do NOT roll back the DB
     * transaction (which has already committed by the time this is called).
     * A dead-letter / retry strategy belongs at the Kafka producer / consumer level.
     */
    public void publishStockEvent(StockEvent event) {
        String key = String.valueOf(event.getProductId());

        try {
            kafkaTemplate.send(TOPIC, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish StockEvent [eventId={}, type={}, product={}]: {}",
                                    event.getEventId(), event.getMovementType(),
                                    event.getProductId(), ex.getMessage());
                        } else {
                            log.debug("StockEvent published [eventId={}, type={}, product={}, partition={}, offset={}]",
                                    event.getEventId(), event.getMovementType(),
                                    event.getProductId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("CRITICAL: Kafka broker is unreachable. Event not sent: {}", e.getMessage());
            // We swallow the exception here so the database transaction doesn't roll back!
        }
    }
}
