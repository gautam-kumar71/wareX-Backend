package com.inventory.purchaseorder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;

    @KafkaListener(topics = "payment-events", groupId = "purchase-order-service-group")
    @Transactional
    public void consumePaymentEvent(String payload) {
        try {
            PaymentProcessedEvent event = objectMapper.readValue(payload, PaymentProcessedEvent.class);
            if (!"PAYMENT_PROCESSED".equalsIgnoreCase(event.eventType()) || event.invoiceNumber() == null) {
                return;
            }

            Invoice invoice = invoiceService.markInvoicePaid(event.invoiceNumber(), event.transactionId());
            log.info("Invoice marked as PAID after payment event: invoiceNumber={}, transactionId={}",
                    invoice.getInvoiceNumber(), event.transactionId());
        } catch (Exception ex) {
            log.error("Failed to process payment event payload", ex);
        }
    }

    private record PaymentProcessedEvent(String eventType, String transactionId, String invoiceNumber) {
    }
}
