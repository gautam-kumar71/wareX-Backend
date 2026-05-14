package com.inventory.alert.dto.response;

import com.inventory.alert.entity.Alert;

import java.time.Instant;

public record AlertResponse(
        Long id,
        String title,
        String message,
        String type,
        boolean read,
        Instant createdAt
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getTitle(),
                alert.getMessage(),
                normalizeType(alert.getType()),
                alert.isRead(),
                alert.getCreatedAt()
        );
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "INFO";
        }

        return switch (type.trim().toUpperCase()) {
            case "PAYMENT", "CRITICAL", "ERROR" -> "CRITICAL";
            case "STOCK", "WARNING", "LOW_STOCK", "OVERSTOCK" -> "WARNING";
            default -> "INFO";
        };
    }
}
