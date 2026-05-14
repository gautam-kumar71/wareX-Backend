package com.inventory.payment.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    void onCreate_setsCreatedAtAndDefaultPendingStatus() {
        Payment payment = Payment.builder()
                .transactionId("TXN-1")
                .invoiceNumber("INV-1")
                .amount(BigDecimal.TEN)
                .paymentMethod("UPI")
                .build();

        payment.onCreate();

        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void onCreate_preservesExistingStatus() {
        Payment payment = Payment.builder()
                .transactionId("TXN-2")
                .invoiceNumber("INV-2")
                .amount(BigDecimal.ONE)
                .paymentMethod("CARD")
                .status(PaymentStatus.COMPLETED)
                .build();

        payment.onCreate();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
