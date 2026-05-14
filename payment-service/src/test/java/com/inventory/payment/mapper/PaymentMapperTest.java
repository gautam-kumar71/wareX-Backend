package com.inventory.payment.mapper;

import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.entity.Payment;
import com.inventory.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapperImpl mapper = new PaymentMapperImpl();

    @Test
    void toResponse_mapsFields() {
        Payment payment = Payment.builder()
                .id(1L)
                .transactionId("TXN-1")
                .invoiceNumber("INV-1")
                .amount(BigDecimal.TEN)
                .paymentMethod("UPI")
                .status(PaymentStatus.COMPLETED)
                .referenceNotes("notes")
                .processedBy("alice")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.transactionId()).isEqualTo("TXN-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
