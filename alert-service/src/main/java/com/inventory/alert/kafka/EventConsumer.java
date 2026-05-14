package com.inventory.alert.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.alert.entity.Alert;
import com.inventory.alert.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "alert-service-group")
    public void consumePaymentEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = readText(event, "eventId");
            String status = event.path("status").asText();
            String txn = event.path("transactionId").asText();

            if ("FAILED".equals(status)) {
                createBroadcastAlertIfAbsent(
                        eventId,
                        "Payment Failed",
                        "Payment for transaction " + txn + " has failed.",
                        "PAYMENT"
                );
                log.info("Alert created for failed payment: {}", txn);
            }
        } catch (Exception e) {
            log.error("Failed to process payment event", e);
        }
    }

    @KafkaListener(topics = "stock-events", groupId = "alert-service-group")
    public void consumeStockEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = readText(event, "eventId");
            int qtyAfter = readQuantityAfter(event);
            long productId = event.path("productId").asLong();
            String productName = readText(event, "productName");
            String warehouseName = readText(event, "warehouseName");
            boolean lowStock = event.path("lowStock").asBoolean(false);
            boolean overstock = event.path("overstock").asBoolean(false);

            if (lowStock) {
                createBroadcastAlertIfAbsent(
                        eventId,
                        "Low Stock Warning",
                        buildLowStockMessage(productId, productName, warehouseName, qtyAfter),
                        "LOW_STOCK"
                );
                log.info("Alert created for low stock: Product {}", productId);
                return;
            }

            if (overstock) {
                createBroadcastAlertIfAbsent(
                        eventId,
                        "Overstock Warning",
                        buildOverstockMessage(productId, productName, warehouseName, qtyAfter),
                        "OVERSTOCK"
                );
                log.info("Alert created for overstock: Product {}", productId);
            }
        } catch (Exception e) {
            log.error("Failed to process stock event", e);
        }
    }

    private String buildLowStockMessage(long productId, String productName, String warehouseName, int qtyAfter) {
        String resolvedProductName = (productName != null && !productName.isBlank())
                ? productName
                : "Product #" + productId;

        if (warehouseName != null && !warehouseName.isBlank()) {
            return resolvedProductName + " is running low in " + warehouseName
                    + ". Current quantity: " + qtyAfter;
        }

        return resolvedProductName + " is running low. Current quantity: " + qtyAfter;
    }

    private String buildOverstockMessage(long productId, String productName, String warehouseName, int qtyAfter) {
        String resolvedProductName = (productName != null && !productName.isBlank())
                ? productName
                : "Product #" + productId;

        if (warehouseName != null && !warehouseName.isBlank()) {
            return resolvedProductName + " has reached high stock levels in " + warehouseName
                    + ". Current quantity: " + qtyAfter;
        }

        return resolvedProductName + " has reached high stock levels. Current quantity: " + qtyAfter;
    }

    private void createBroadcastAlertIfAbsent(String eventId, String title, String message, String type) {
        if (eventId != null && !eventId.isBlank() && alertRepository.existsByEventId(eventId)) {
            log.debug("Skipping duplicate alert eventId={}", eventId);
            return;
        }

        Alert alert = Alert.builder()
                .eventId(eventId)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .build();
        alertRepository.save(alert);
    }

    private int readQuantityAfter(JsonNode event) {
        JsonNode newQuantity = event.path("newQuantity");
        if (!newQuantity.isMissingNode() && !newQuantity.isNull()) {
            return newQuantity.asInt();
        }

        // Backward-compatible fallback for older payload shapes.
        JsonNode quantityAfter = event.path("quantityAfter");
        if (!quantityAfter.isMissingNode() && !quantityAfter.isNull()) {
            return quantityAfter.asInt();
        }

        return 0;
    }

    private String readText(JsonNode event, String fieldName) {
        JsonNode node = event.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }
}
