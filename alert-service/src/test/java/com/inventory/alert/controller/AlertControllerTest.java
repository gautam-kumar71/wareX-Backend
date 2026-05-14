package com.inventory.alert.controller;

import com.inventory.alert.dto.response.AlertResponse;
import com.inventory.alert.entity.Alert;
import com.inventory.alert.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertController alertController;

    @Test
    void getUserAlerts_returnsBroadcastAlertsForAnonymousUser() {
        Alert alert = alert(1L, null, "LOW_STOCK", false);
        when(alertRepository.findByUserIdIsNullOrderByCreatedAtDesc()).thenReturn(List.of(alert));

        List<AlertResponse> response = alertController.getUserAlerts(null).getBody().data();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1L);
            assertThat(item.type()).isEqualTo("WARNING");
        });
    }

    @Test
    void getUserAlerts_returnsUserAndBroadcastAlertsForAuthenticatedUser() {
        Alert alert = alert(2L, "user-1", "PAYMENT", true);
        when(alertRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtDesc("user-1")).thenReturn(List.of(alert));

        List<AlertResponse> response = alertController.getUserAlerts(auth("user-1")).getBody().data();

        assertThat(response).extracting(AlertResponse::type).containsExactly("CRITICAL");
    }

    @Test
    void markAsRead_marksUnreadAlertWhenOwnerMatches() {
        Alert alert = alert(3L, "user-1", "SYSTEM", false);
        when(alertRepository.findById(3L)).thenReturn(Optional.of(alert));

        String message = alertController.markAsRead(3L, auth("user-1")).getBody().message();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
        assertThat(message).isEqualTo("Alert marked as read");
    }

    @Test
    void markAsRead_skipsSaveWhenAlertAlreadyRead() {
        Alert alert = alert(4L, null, "SYSTEM", true);
        when(alertRepository.findById(4L)).thenReturn(Optional.of(alert));

        alertController.markAsRead(4L, auth("user-1"));

        verify(alertRepository, never()).save(alert);
    }

    @Test
    void markAsRead_rejectsAccessForDifferentUser() {
        Alert alert = alert(5L, "owner", "SYSTEM", false);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertController.markAsRead(5L, auth("other")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN")
                .hasMessageContaining("do not have access");
    }

    @Test
    void clearAllAlerts_deletesUserAndBroadcastAlertsForAuthenticatedUser() {
        String message = alertController.clearAllAlerts(auth("user-9")).getBody().message();

        verify(alertRepository).deleteByUserIdOrUserIdIsNull("user-9");
        assertThat(message).isEqualTo("All alerts cleared");
    }

    @Test
    void clearAllAlerts_deletesBroadcastAlertsForAnonymousUser() {
        alertController.clearAllAlerts(null);

        verify(alertRepository).deleteByUserIdIsNull();
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null);
    }

    private Alert alert(Long id, String userId, String type, boolean read) {
        return Alert.builder()
                .id(id)
                .userId(userId)
                .title("Alert")
                .message("Message")
                .type(type)
                .isRead(read)
                .createdAt(Instant.parse("2026-05-02T00:00:00Z"))
                .build();
    }
}
