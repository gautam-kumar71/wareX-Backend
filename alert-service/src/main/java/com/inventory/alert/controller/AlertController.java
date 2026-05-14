package com.inventory.alert.controller;

import com.inventory.alert.dto.response.ApiResponse;
import com.inventory.alert.dto.response.AlertResponse;
import com.inventory.alert.entity.Alert;
import com.inventory.alert.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getUserAlerts(Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;

        List<Alert> alerts = userId == null || userId.isBlank()
                ? alertRepository.findByUserIdIsNullOrderByCreatedAtDesc()
                : alertRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(userId);

        List<AlertResponse> response = alerts.stream()
                .map(AlertResponse::from)
                .toList();

        log.debug("Returning {} alerts for user={}", response.size(), userId == null ? "broadcast" : userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id,
                                                        Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Alert not found"));

        boolean canMarkRead = alert.getUserId() == null
                || (userId != null && userId.equals(alert.getUserId()));
        if (!canMarkRead) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this alert");
        }

        if (!alert.isRead()) {
            alert.setRead(true);
            alertRepository.save(alert);
            log.info("Alert marked as read: id={}, user={}", id, userId);
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Alert marked as read"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearAllAlerts(Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;

        if (userId == null || userId.isBlank()) {
            alertRepository.deleteByUserIdIsNull();
        } else {
            alertRepository.deleteByUserIdOrUserIdIsNull(userId);
        }

        log.info("Alerts cleared for user={}", userId == null ? "broadcast" : userId);

        return ResponseEntity.ok(ApiResponse.success(null, "All alerts cleared"));
    }
}
