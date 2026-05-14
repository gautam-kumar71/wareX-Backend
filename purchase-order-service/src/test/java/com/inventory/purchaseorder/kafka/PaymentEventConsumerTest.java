package com.inventory.purchaseorder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private InvoiceService invoiceService;

    @Test
    void consumePaymentEvent_marksInvoicePaidForProcessedEvent() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(new ObjectMapper(), invoiceService);
        org.mockito.Mockito.when(invoiceService.markInvoicePaid("INV-1", "TXN-1"))
                .thenReturn(Invoice.builder().invoiceNumber("INV-1").amount(BigDecimal.TEN).dueDate(LocalDate.now()).build());

        consumer.consumePaymentEvent("""
                {"eventType":"PAYMENT_PROCESSED","transactionId":"TXN-1","invoiceNumber":"INV-1"}
                """);

        verify(invoiceService).markInvoicePaid("INV-1", "TXN-1");
    }

    @Test
    void consumePaymentEvent_ignoresUnrelatedOrBrokenPayload() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(new ObjectMapper(), invoiceService);
        consumer.consumePaymentEvent("""
                {"eventType":"OTHER","transactionId":"TXN-1","invoiceNumber":"INV-1"}
                """);
        consumer.consumePaymentEvent("not-json");

        verify(invoiceService, never()).markInvoicePaid("INV-1", "TXN-1");
    }
}
