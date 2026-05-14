package com.inventory.purchaseorder.kafka;

import com.inventory.purchaseorder.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes OrderEvent messages to the "order-events" Kafka topic.
 *
 * Key = purchaseOrderId (string) — ensures all events for the same
 * purchase order land on the same partition and are processed in order.
 *
 * Producer configured with:
 *   - acks=all + enable.idempotence=true → exactly-once at producer level
 *   - retries=3 → automatic retry on transient broker failures
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOrderEvent(OrderEvent event) {
        String key = event.getPurchaseOrderId().toString();

        kafkaTemplate.send(orderEventsTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderEvent [eventId={}, type={}, orderId={}]: {}",
                                event.getEventId(),
                                event.getEventType(),
                                event.getPurchaseOrderId(),
                                ex.getMessage());
                    } else {
                        log.debug("OrderEvent published [eventId={}, type={}, partition={}, offset={}]",
                                event.getEventId(),
                                event.getEventType(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}