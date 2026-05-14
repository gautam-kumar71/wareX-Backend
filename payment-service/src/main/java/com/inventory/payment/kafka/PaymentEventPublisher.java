package com.inventory.payment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    public void publishEvent(PaymentEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }

        log.info("Publishing payment event [{}] for transaction [{}]", event.getEventType(), event.getTransactionId());
        
        kafkaTemplate.send(paymentEventsTopic, event.getTransactionId(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish payment event [{}]", event.getEventId(), ex);
            }
        });
    }
}
