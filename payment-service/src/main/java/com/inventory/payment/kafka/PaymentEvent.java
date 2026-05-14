package com.inventory.payment.kafka;

import com.inventory.payment.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("java:S1068")
public class PaymentEvent {
    private String eventId; // unique UUID
    private String eventType; // PAYMENT_PROCESSED, PAYMENT_FAILED
    private String transactionId;
    private String invoiceNumber;
    private BigDecimal amount;
    private PaymentStatus status;
    private String triggeredBy;
    private Instant occurredAt;
}
