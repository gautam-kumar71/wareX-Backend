package com.inventory.payment.dto;

import com.inventory.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String transactionId,
        String invoiceNumber,
        BigDecimal amount,
        String paymentMethod,
        PaymentStatus status,
        String referenceNotes,
        String processedBy,
        Instant createdAt
) {}
